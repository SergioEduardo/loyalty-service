# loyalty-service (L2)

Sigue el Development Standard v1.0 de Axioma (skill `axioma-dev-standard`): hexagonal, CQRS ligero,
`ReactiveCrudRepository` para comandos, `DatabaseClient` + SQL externo (`resources/sql`) para consultas.

Reglas de este servicio:
- El negocio (`tenantId`) de cada operación sale siempre del token (`tid`), nunca del cuerpo ni de la URL.
  Única excepción: la API admin (`/api/admin/**`), que es de operador y recibe el negocio en la URL.
- El historial de movimientos es inmutable (trigger en la base).
- Los avisos (`TenantChanged`, `CardChanged`) se escriben en `loyalty.outbox` en la misma transacción.
- Nunca registrar contraseñas, tokens ni cadenas de conexión.
- Fuente de verdad del diseño: página de Notion "Programa de lealtad con Wallet (fuente de verdad)".

API admin (`/api/admin/**`, la usa n8n):
- La protege `AdminAuthFilter`: ID token de Google (RS256, issuer `https://accounts.google.com`),
  audiencia = `ADMIN_AUDIENCE`, `email_verified` y correo en `ADMIN_ALLOWED_EMAILS`. Sin secretos compartidos.
- Sin token o token inválido -> 401 `UNAUTHORIZED`; correo no permitido -> 403 `FORBIDDEN`.
  Si falta `ADMIN_AUDIENCE` o `ADMIN_ALLOWED_EMAILS`, la API admin queda cerrada (403).
- Verificar que el negocio de la URL exista y esté activo; la cuenta debe pertenecer a ese negocio.
- Contraseñas: bcrypt en el scheduler `passwordHashScheduler`, nunca en el event loop; 8+ caracteres
  y máximo 72 bytes. Commands y requests con contraseña sobrescriben `toString`.
- Desactivar o cambiar la contraseña revoca todas las sesiones de la cuenta en la misma transacción.
- `.gitignore` usa `/out/` y `/build/` anclados: `out/` sin ancla excluiría `domain/port/out` y `adapter/out`.

Sesiones del personal (`/api/auth/**`, públicas salvo `/me`):
- Access token JWT HS256 (`JWT_SIGNING_KEY`, 32+ bytes; sin ella el login responde 503 `AUTH_UNAVAILABLE`),
  claims `iss=loyalty-service`, `sub`, `tid`, `role`, `sid`. `StaffAuthFilter` protege `/api/auth/me` y
  `/api/staff/**`: además de firma/issuer/exp exige que la sesión `sid` siga vigente y la cuenta y el negocio
  activos. Endpoints de staff: leer el `StaffPrincipal` del atributo `StaffAuthFilter.PRINCIPAL_ATTRIBUTE`
  (el `tenantId` sale de ahí) y exigir rol con `@RequireRole(UserRole.OWNER)`; OWNER cumple cualquier rol.
- Refresh token opaco (256 bits, base64url); en la base solo su SHA-256. Rotación en cada refresh; un token
  ya rotado que vuelve a llegar revoca la sesión completa (`loyalty.session_rotated_tokens`).
- Login: negocio/usuario inexistente o inactivo y contraseña incorrecta dan el mismo `INVALID_CREDENTIALS`,
  con exactamente un bcrypt por intento (ficticio si no hay cuenta). Bloqueo por (negocio, usuario) en
  `loyalty.login_attempts` -> 429 `TOO_MANY_ATTEMPTS` con `Retry-After`.
- Nunca registrar tokens, hashes de tokens ni `JWT_SIGNING_KEY`. Migraciones nuevas en `V{n}__*.sql`;
  nunca editar una ya aplicada.

Perfil del negocio (`/api/staff/tenant/**`, solo OWNER; `TenantProfileController`):
- `GET /api/staff/tenant`, `PUT /identity`, `PUT /settings`, `PUT /logo` (multipart, campo `file`),
  `DELETE /logo` (204, idempotente). El negocio sale del `StaffPrincipal`; nunca de la ruta, query ni cuerpo.
- Todo cambio al negocio pasa por `TenantChangeWriter`: fila bloqueada (`FOR UPDATE`), update y `TenantChanged`
  en la misma transacción; si nada cambia, no escribe ni avisa.
- Logo: formato por magic bytes (`ImageFormat`), nunca por Content-Type/extensión; solo PNG/JPEG/WebP (WebP con
  TwelveMonkeys `imageio-webp`). Dimensiones leídas de la cabecera antes de decodificar (máx. 4000 px por lado).
  El procesamiento corre en `imageProcessingScheduler` (2 hilos), nunca en el event loop.
- Objeto `tenants/{tenantId}/logo-{sha256}.png`, Cache-Control público de 1 año (inmutable). Se sube fuera de
  la transacción; el anterior se borra después del commit y un fallo al borrarlo solo se registra.
- Bucket por `LOGOS_BUCKET` y ADC (sin llaves). `logo_url` (pública) y `logo_object_key` se llenan juntas.

Manifest de la PWA de caja (`GET /caja/{slug}/manifest.webmanifest`; `TenantManifestController`):
- Público y fuera de `/api` (Firebase Hosting lo reenvía con la ruta original). No hay cadena de Spring Security
  web; si se agrega una o se amplían los prefijos de `AdminAuthFilter`/`StaffAuthFilter`, `/caja/**` debe seguir público.
- `GetTenantManifestHandler` reutiliza `GetTenantIdentityHandler`; `TenantManifest.of` arma el contenido. Solo datos
  de la identidad pública. Inexistente, inactivo o slug inválido -> 404 sin cuerpo (no `ApiError`).
- `application/manifest+json`, `Cache-Control: public, max-age=300`. `short_name` máx. 12 code points sin partir palabras.

Activación por enlace (`POST .../users` sin `password`, `POST .../activation-link`, `/api/auth/activation/inspect`,
`/api/auth/activate`):
- Pendiente = `credential_hash` NULL (V4; sin columna de estado). Login de una pendiente -> `INVALID_CREDENTIALS`
  idéntico (bcrypt ficticio). `find-active-principal.sql` y `rotate-refresh-token.sql` exigen credencial.
- Token: 256 bits base64url (`SecureActivationTokenGenerator`), en `loyalty.activation_tokens` solo su SHA-256,
  72 h, un uso; emitir otro o `reset-password` invalidan los anteriores (`used_at`). Nunca registrar el token,
  su hash ni la `activationUrl`; commands/requests/responses con token sobrescriben `toString`.
- Cualquier fallo de inspect/activate -> 400 `INVALID_ACTIVATION` con el mismo cuerpo, sin distinguir causa.
  `activate`: `TokenIssuer.ensureReady()` y política (`PasswordPolicy`, la misma del alta) antes de consumir;
  bcrypt fuera de la transacción; `AccountActivationWriter` hace consumo + hash + revocación de sesiones +
  invalidación de enlaces en una transacción; luego `SessionStarter` (compartido con el login).
- Límite (`ActivationThrottle`, `loyalty.activation_attempts`): por slug + IP y por slug, exista o no el slug.
- `APP_WEB_BASE_URL` arma `activationUrl` (`/caja/{slug}/activar#t={token}`); sin ella se omite el campo.

Publicación de avisos (outbox → Pub/Sub, diseño 12.11; `PublishPendingEventsHandler`):
- Reclamo único para los dos caminos (`sql/outbox/claim-due.sql`, `claim-by-ids.sql`): una sentencia con
  `FOR UPDATE SKIP LOCKED` que corre `next_attempt_at` el plazo `OUTBOX_CLAIM_LEASE_SECONDS`. Nunca publicar dentro
  de una transacción ni retener bloqueos durante la llamada a Pub/Sub; `OutboxEventAdapter` va en autocommit.
- Camino rápido: los handlers que escriben un aviso llaman a `OutboxFastPath.publish(eventId)` después del commit
  (por eso el writer transaccional devuelve el id: `TenantUpdate.eventId`, `CreatedTenant`). Nunca falla ni cambia
  la respuesta; tiene tope `OUTBOX_FAST_PATH_TIMEOUT_MS`. Un aviso nuevo de otro caso de uso debe seguir el mismo patrón.
- Falla: attempts+1 y espera 5 s × 2^(attempts−1) con tope 15 min (`OutboxRetryPolicy`); al llegar a
  `OUTBOX_MAX_ATTEMPTS`, `FAILED` + log ERROR. Logs con conteos e ids; nunca el payload (`OutboxEvent.toString` lo omite).
- Mensaje: cuerpo = payload; atributos `eventType`, `tenantId`, `cardId` ("" si no hay), `seq` (= id del aviso).
- Sin `PUBSUB_TOPIC`, publicación desactivada (`PubSubEventAdapter.disabled()`); el endpoint responde `disabled:true`.
- `/internal/**` lo protege `InternalAuthFilter` (ID token de Google, `OUTBOX_PUBLISH_AUDIENCE` y
  `OUTBOX_PUBLISH_ALLOWED_EMAILS`; vacías → 403). La validación del token es `GoogleIdTokenVerifier`, compartida con
  `AdminTokenVerifier`. Todo endpoint nuevo bajo `/internal` queda detrás de ese filtro.
- Pendiente: limpieza de avisos `PUBLISHED` antiguos y alerta sobre `FAILED`.
- `TenantChanged` lleva el `slug` (el wallet-service forma con él la clase de Google); su contrato JSON lo fija
  `TenantChangedTest`: no quitar ni renombrar campos. Pendiente (bloque de diseño de pases): republicar el
  `TenantChanged` de los negocios existentes sin SQL manual, p. ej. `POST /internal/tenants/republish` que escriba un
  aviso **nuevo** por negocio (seq mayor que el `last_seq` del wallet), sin el atajo de "nada cambió".

Alta de la tarjeta del cliente (`/api/public/enroll/**`, públicos; diseño 12.6 y 12.7; `EnrollmentController`):
- `POST /verification {slug, phone}` → `{verificationId, expiresAt}`; `POST /card {slug, phone, name, verificationId, code}`
  → `{isNew, apple: {url, expiresAt}, google: {url} | null}`. Nunca exponer cardId (salvo dentro del enlace del wallet), nombre ni
  teléfono. El slug va en el cuerpo, no en la ruta (RequestLogFilter registra la ruta). Identidad del negocio para la
  página de alta: el `GET /api/public/tenants/{slug}/identity` existente.
- Teléfono: `PhoneRules` (libphonenumber, región MX, acepta `+52 1`). Negocio: `ActiveTenantGuard.requireBySlug`.
- Código: `SecureVerificationCodeGenerator` (6 dígitos); en la base solo `HmacVerificationCodeHasher` (HMAC-SHA256 hex,
  llave `OTP_HASH_SALT` ≥ 32 bytes, sobre `"{tenantId}|{phoneE164}|{code}"`); sin sal → 503 `VERIFICATION_UNAVAILABLE`.
- Límites (`EnrollRateLimits`, `loyalty.enroll_rate_limits`, ventana fija 1 h, IP como SHA-256): IP y negocio al pedir
  código, IP al pedir tarjeta. Teléfono: `PhoneVerificationIssuer` (bloqueo `pg_advisory_xact_lock` + conteo por
  `created_at` + insert en una transacción). Excedido → 429 `RATE_LIMITED` con `Retry-After` (`RateLimitedException`).
- `VerificationCodeCheck`: discrepancia de id/negocio/teléfono → `INVALID_CODE` idéntico; vencida → `CODE_EXPIRED`;
  validada → no compara; si no, `UPDATE attempts = attempts + 1` antes de comparar en tiempo constante; al máximo →
  `CodeAttemptsExhaustedException` (TOO_MANY_ATTEMPTS **400**, "Pide un código nuevo"; el 429 de login/activación es
  `TooManyAttemptsException`).
- `CardEnrollmentWriter`: INSERT ON CONFLICT DO NOTHING y, si no insertó, SELECT (otra sentencia) en una transacción
  corta; commit antes del wallet-service. Nunca llamar al wallet-service dentro de una transacción.
- `WalletPassIssuerPort` → `HttpWalletPassIssuer` (`WALLET_SERVICE_URL`/internal/passes, ID token de ADC con
  `WALLET_SERVICE_AUDIENCE` vía `ServiceIdTokenSource`, tope `WALLET_ISSUE_TIMEOUT_MS` (20 s) por intento y para el token;
  un reintento solo por timeout/`WebClientRequestException`, nunca ante una respuesta HTTP: IssuePass es idempotente
  por cardId; logs con `attempt` y `durationMs`). 409 TEMPLATE_NOT_READY → 409;
  lo demás → 503 `PASS_SERVICE_UNAVAILABLE` (401/403 con log ERROR sin detalles). Sin URL: `DisabledWalletPassIssuer`.
- Google Wallet es opcional: `google` ausente (wallet-service anterior), null, `{url: null}` o malformado →
  `IssuedPass.googleUrl` null y `google: null` en la respuesta; nunca falla el alta ni da `PASS_SERVICE_UNAVAILABLE`
  (solo Apple decide el éxito). El enlace de Google lleva un JWT firmado: nunca registrarlo (logs solo `google=true|false`).
- Envío del código: un puerto (`PhoneVerificationSenderPort`) y `VerificationChannelPolicy` (`EnrollConfig`).
  `VERIFICATION_SENDER` = canal por defecto: `disabled` (defecto, 503), `log` (reemplaza a todos los canales y escribe
  `channel` y código en log INFO; **solo `APP_ENV=dev`, si no la app no arranca**), `whatsapp` o `sms`;
  `VERIFICATION_ENABLED_CHANNELS` = canales disponibles. Canal desconocido, por defecto fuera de la lista o canal
  habilitado sin credenciales → la app no arranca. `channel` en la solicitud es opcional; desconocido/no habilitado
  → 400 `CHANNEL_NOT_AVAILABLE` antes de contar límites. Respuesta aditiva `channel` + `availableChannels`.
- Los límites (teléfono, IP, negocio), la vigencia y los intentos no dependen del canal; `phone_verifications.channel`
  (V6) es solo auditoría. Fallo de envío → la verificación se queda contando; `VerificationDeliveryException`:
  400 `RECIPIENT_UNREACHABLE` (Meta 131026, Twilio 21211) o 503 `VERIFICATION_UNAVAILABLE`, sin detalles del proveedor.
- `WhatsAppPhoneVerificationSender` (Cloud API, plantilla de autenticación: código en body y botón `url` index 0):
  un reintento **solo** si no hubo conexión. `TwilioSmsPhoneVerificationSender` (Programmable Messaging, nunca
  Twilio Verify): sin reintentos; texto ASCII GSM-7 de un segmento (`SmsVerificationText`). 2xx/201 = aceptado,
  no entregado. Tokens (`WHATSAPP_ACCESS_TOKEN`, `TWILIO_AUTH_TOKEN`) de Secret Manager, cableados en loyalty-infra.
- Privacidad: nunca registrar teléfono (ni enmascarado), nombre, código (fuera de `log`), hash, IP, texto del SMS,
  tokens de proveedores ni verificationId junto al teléfono; para correlacionar: tenantId, verificationId, channel,
  estado HTTP y `providerCode`. Commands/requests/payloads/propiedades con esos datos sobrescriben `toString`.
- Pendiente: seguimiento de entrega (webhooks de Meta, `StatusCallback` de Twilio), limpieza periódica de
  verificaciones vencidas y de contadores de límite.

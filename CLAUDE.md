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

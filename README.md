# loyalty-service

Servicio del programa de lealtad: negocios, identidad, cuentas, sesiones, tarjetas y puntos.
Java 21, Spring Boot + WebFlux, R2DBC, Gradle Kotlin DSL, arquitectura hexagonal
(estándar de desarrollo de Axioma v1.0). Base de datos: Neon (schema `loyalty`).

## Estado (entrega 2, bloque 4)
- Esqueleto hexagonal, errores normalizados, `requestId` en logs, migraciones Flyway.
- `GET /api/public/tenants/{slug}/identity`: identidad pública del negocio.
- API admin para n8n: alta de negocios y cuentas del personal (protegida con ID tokens de Google).
- Login, sesiones (access + refresh con rotación) y roles del personal.
- Perfil del negocio para el dueño: identidad, configuración del programa y logo (Cloud Storage).
- Manifest de la PWA de caja por negocio (`/caja/{slug}/manifest.webmanifest`).
- Activación de cuentas por enlace de un solo uso (la persona elige su contraseña).
- Publicación de los avisos del outbox en Pub/Sub (camino rápido + red de seguridad con Cloud Scheduler).
- Alta de la tarjeta del cliente: verificación del teléfono por código (12.6) y tarjeta + pase de Apple
  vía el wallet-service (12.7).

## Endpoints
| Método y ruta | Respuesta | Descripción |
|---|---|---|
| `GET /api/public/tenants/{slug}/identity` | 200 | Identidad pública del negocio (sin autenticación) |
| `GET /caja/{slug}/manifest.webmanifest` | 200 | Manifest de la PWA de caja del negocio (sin autenticación; 404 sin cuerpo si no existe o está inactivo) |
| `POST /api/admin/tenants` | 201 | Alta de negocio y aviso `TenantChanged` en `loyalty.outbox` |
| `POST /api/admin/tenants/{tenantId}/users` | 201 | Alta de cuenta (`CASHIER` u `OWNER`); sin `password` queda pendiente y devuelve `activation` |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/deactivate` | 204 | Desactiva la cuenta y revoca sus sesiones |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/reset-password` | 204 | Cambia la contraseña, revoca sus sesiones e invalida sus enlaces de activación |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/activation-link` | 200 | Enlace de activación nuevo (reenvío o cambio de contraseña por enlace); invalida los anteriores |
| `POST /api/auth/login` | 200 | Inicio de sesión del personal: `{slug, username, password}` |
| `POST /api/auth/refresh` | 200 | Renueva la sesión: `{refreshToken}`; devuelve un refresh nuevo |
| `POST /api/auth/logout` | 204 | Cierra la sesión: `{refreshToken}`; idempotente |
| `POST /api/auth/activation/inspect` | 200 | A quién pertenece un enlace: `{slug, token}` → `{tenantName, username}` |
| `POST /api/auth/activate` | 200 | Elige la contraseña con el enlace: `{slug, token, password}`; misma respuesta que `/login` |
| `GET /api/auth/me` | 200 | Quién soy, según el access token: `{userId, tenantId, role, sessionId}` |
| `GET /api/staff/tenant` | 200 | Perfil del negocio del token (solo OWNER) |
| `PUT /api/staff/tenant/identity` | 200 | Nombre, descripción y color (solo OWNER); devuelve el perfil |
| `PUT /api/staff/tenant/settings` | 200 | Meta de puntos, premio, espera y ventana para deshacer (solo OWNER); devuelve el perfil |
| `PUT /api/staff/tenant/logo` | 200 | Sube el logo: `multipart/form-data`, campo `file` (solo OWNER); devuelve el perfil |
| `DELETE /api/staff/tenant/logo` | 204 | Quita el logo (solo OWNER); idempotente |
| `POST /api/public/enroll/verification` | 200 | Pide un código para el teléfono (sin autenticación): `{slug, phone, channel?}` → `{verificationId, expiresAt, channel, availableChannels}` |
| `POST /api/public/enroll/card` | 200 | Da la tarjeta con el código (sin autenticación): `{slug, phone, name, verificationId, code}` → `{isNew, apple, google}` |
| `POST /internal/outbox/publish` | 200 | Publica un lote de avisos pendientes (Cloud Scheduler): `{claimed, published, failed}` |

Cuerpos (JSON):
```jsonc
// POST /api/admin/tenants  (cooldownSeconds default 0, undoWindowSeconds default 300)
{ "slug": "mi-cafe", "name": "Mi Café", "description": "Bienvenido", "brandColor": "#112233",
  "pointsGoal": 10, "rewardText": "Café gratis", "cooldownSeconds": 0, "undoWindowSeconds": 300 }
// POST .../users  (username se guarda recortado y en minúsculas; name opcional, default = username;
//                 password opcional: sin ella la cuenta queda pendiente de activación)
{ "username": "ana", "password": "al-menos-8", "role": "CASHIER", "name": "Ana" }
// Respuesta 201 (activation solo si no llegó password)
{ "id": "…", "tenantId": "…", "name": "Ana", "username": "ana", "role": "OWNER", "active": true,
  "activation": { "token": "<43 caracteres base64url>", "expiresAt": "2026-10-16T12:00:00Z",
                  "activationUrl": "https://axioma-loyalty-dev-web.web.app/caja/mi-cafe/activar#t=<token>" } }
// Respuesta de POST .../activation-link: el mismo objeto que "activation"
// POST .../reset-password
{ "password": "nueva-clave" }
// Respuesta de GET/PUT /api/staff/tenant/**
{ "slug": "mi-cafe", "name": "Mi Café", "description": "Bienvenido",
  "logoUrl": "https://storage.googleapis.com/<bucket>/tenants/<tenantId>/logo-<sha256>.png", "brandColor": "#112233",
  "settings": { "pointsGoal": 10, "rewardText": "Café gratis", "cooldownSeconds": 0, "undoWindowSeconds": 300 } }
// PUT /api/staff/tenant/identity  (name 2–60, description opcional ≤ 140, brandColor #RRGGBB obligatorio; se guarda en minúsculas)
{ "name": "Mi Café", "description": "Bienvenido", "brandColor": "#A1B2C3" }
// PUT /api/staff/tenant/settings  (todos obligatorios; pointsGoal > 0, segundos ≥ 0, rewardText ≤ 100)
{ "pointsGoal": 10, "rewardText": "Café gratis", "cooldownSeconds": 0, "undoWindowSeconds": 300 }
```
Errores: `{ "code": "...", "message": "..." }` (solo `CHANNEL_NOT_AVAILABLE` y `RECIPIENT_UNREACHABLE`
agregan `availableChannels` y, el segundo, `channel`; ver "Alta de la tarjeta"). 400 `INVALID_SLUG`, `INVALID_TEXT`,
`INVALID_BRAND_COLOR`, `INVALID_SETTINGS`, `INVALID_USER`, `WEAK_PASSWORD`, `INVALID_REQUEST`, `INVALID_ACTIVATION`,
`INVALID_IMAGE`, `IMAGE_DIMENSIONS_TOO_LARGE`, `INVALID_PHONE`, `INVALID_NAME`, `INVALID_CODE`, `CODE_EXPIRED`,
`CHANNEL_NOT_AVAILABLE`, `RECIPIENT_UNREACHABLE`, `TOO_MANY_ATTEMPTS` (solo el alta: intentos del código agotados); 401 `UNAUTHORIZED`, `INVALID_CREDENTIALS`,
`INVALID_SESSION`; 403 `FORBIDDEN`; 404 `TENANT_NOT_FOUND`, `USER_NOT_FOUND`; 409 `SLUG_ALREADY_EXISTS`,
`USERNAME_TAKEN`, `USER_INACTIVE`, `TEMPLATE_NOT_READY`; 413 `LOGO_TOO_LARGE`; 415 `UNSUPPORTED_IMAGE_FORMAT`;
429 `TOO_MANY_ATTEMPTS` (login y activación), `RATE_LIMITED` (alta), ambos con `Retry-After`; 500 `INTERNAL_ERROR`;
503 `AUTH_UNAVAILABLE`, `STORAGE_UNAVAILABLE`, `VERIFICATION_UNAVAILABLE`, `PASS_SERVICE_UNAVAILABLE`.

### Sesiones del personal
```text
login ─► { accessToken (JWT, 15 min), refreshToken (opaco, 30 días), tokenType, expiresIn, user, tenant }
          │
          ├─ Authorization: Bearer <accessToken> en /api/auth/me y /api/staff/**
          └─ al vencer: POST /api/auth/refresh {refreshToken} ─► tokens nuevos (el refresh anterior deja de servir)
logout {refreshToken} ─► revoca la sesión
```
- El negocio de cada operación de staff sale del token (`tid`), nunca del cuerpo ni de la URL.
- El access token es JWT HS256 (`iss=loyalty-service`, `sub`=cuenta, `tid`, `role`, `sid`=sesión). En cada
  petición se comprueba además que la sesión siga vigente y la cuenta y el negocio activos: desactivar la
  cuenta o cambiarle la contraseña (API admin) invalida sus tokens de inmediato.
- En la base solo se guarda el SHA-256 del refresh token. Si llega uno ya rotado, se revoca la sesión
  completa y se responde `INVALID_SESSION`: hay que volver a iniciar sesión.
- Negocio inexistente o inactivo, cuenta inexistente o inactiva y contraseña incorrecta responden igual
  (`INVALID_CREDENTIALS`) y tardan lo mismo. 5 fallos de un usuario en 15 minutos lo bloquean hasta que
  vence la ventana (429 `TOO_MANY_ATTEMPTS`); un inicio correcto limpia los fallos.
- Roles: `@RequireRole(UserRole.OWNER)` en un endpoint de staff; OWNER cumple cualquier rol.

```bash
curl -X POST "$URL/api/auth/login" -H "Content-Type: application/json" \
  -d '{"slug":"demo-cafe","username":"caja1","password":"..."}'
curl "$URL/api/auth/me" -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Activación por enlace
```text
n8n: POST /api/admin/tenants/{id}/users {username, role}            (sin password)
     └─► 201 { ..., activation: { token, expiresAt, activationUrl } }
PWA: /caja/{slug}/activar#t={token}   (el token va en el fragmento: no llega a ningún servidor)
     ├─ POST /api/auth/activation/inspect {slug, token}           ─► { tenantName, username }
     └─ POST /api/auth/activate {slug, token, password}           ─► tokens de sesión (igual que /login)
```
- Token de 256 bits en base64url; en `loyalty.activation_tokens` solo su SHA-256. Vence a las 72 h y sirve
  una vez. Emitir otro (`activation-link`) o un `reset-password` del admin invalida los anteriores.
- Cuenta pendiente = cuenta sin credencial (`credential_hash` NULL). El login le responde
  `INVALID_CREDENTIALS` igual que a una contraseña incorrecta (con el mismo bcrypt ficticio) y el filtro de
  staff y el refresh no aceptan sesiones de cuentas sin credencial.
- `activation-link` sobre una cuenta activa es un cambio de contraseña por enlace: la contraseña actual
  sirve hasta que se use el enlace. Cuenta desactivada → 409 `USER_INACTIVE`.
- `activate`, en una transacción: marca el enlace usado (si sigue válido), guarda el hash bcrypt (la cuenta
  queda activada), revoca todas sus sesiones e invalida sus otros enlaces. Después abre la sesión nueva.
  La contraseña sigue la misma política que el alta (`WEAK_PASSWORD`); un rechazo por política no gasta el enlace.
- Inexistente, vencido, usado, de otro negocio, cuenta o negocio inactivos: siempre 400
  `INVALID_ACTIVATION`, con el mismo cuerpo, en inspect y activate.
- Límite de fallos, como el login (429 `TOO_MANY_ATTEMPTS` + `Retry-After`): 5 por slug + IP y 20 por slug
  en 15 minutos. Cuenta aunque el slug no exista. La IP sale del primer valor de `X-Forwarded-For`; como el
  cliente puede falsearlo, el límite por slug es el que no se puede esquivar.
- El token nunca se registra en logs ni va en eventos (solo `tenantId`, `userId` y `expiresAt`).

### Alta de la tarjeta del cliente
Diseño 12.6 y 12.7. Página de alta de la PWA: lee la identidad del negocio de
`GET /api/public/tenants/{slug}/identity` (`{slug, name, description, logoUrl, brandColor}`, solo negocios
activos; si no, 404 `TENANT_NOT_FOUND`) y luego:
```text
POST /api/public/enroll/verification {slug, phone, channel?}
     └─► 200 {verificationId, expiresAt, channel, availableChannels}
         (el código llega por WhatsApp o SMS; vale 10 min)
POST /api/public/enroll/card {slug, phone, name, verificationId, code}
     └─► 200 {isNew, apple: {url, expiresAt}, google: null}
         (apple.url: enlace firmado del wallet-service para descargar el .pkpass; vale 10 min)
```
```jsonc
// POST /api/public/enroll/verification   (phone: con o sin +52; espacios, guiones y paréntesis se ignoran)
// channel (opcional): "whatsapp" | "sms"; sin él, el canal por defecto (VERIFICATION_SENDER)
{ "slug": "mi-cafe", "phone": "55 1234 5678", "channel": "sms" }
// 200 (channel: el usado; availableChannels: los habilitados, en el orden de VERIFICATION_ENABLED_CHANNELS)
{ "verificationId": "3f1c…", "expiresAt": "2026-10-13T12:10:00Z", "channel": "sms", "availableChannels": ["whatsapp", "sms"] }
// 400 canal desconocido o no habilitado (el mismo cuerpo en los dos casos; nunca lleva channel)
// availableChannels: los habilitados, en el orden de VERIFICATION_ENABLED_CHANNELS
{ "code": "CHANNEL_NOT_AVAILABLE", "message": "Ese medio de envío no está disponible.", "availableChannels": ["whatsapp", "sms"] }
// 400 el número no puede recibir por ese canal (Meta 131026, Twilio 21211): la página puede ofrecer otro de availableChannels
// channel: el canal por el que se intentó (el pedido o, sin channel, el de por defecto)
{ "code": "RECIPIENT_UNREACHABLE", "message": "No pudimos enviar el código a ese número por este medio. Prueba con otro.", "channel": "sms", "availableChannels": ["whatsapp", "sms"] }
// POST /api/public/enroll/card
{ "slug": "mi-cafe", "phone": "55 1234 5678", "name": "Ana López", "verificationId": "3f1c…", "code": "042137" }
// 200 (nunca lleva el nombre ni el teléfono; el cardId solo va dentro del enlace del wallet-service)
{ "isNew": true, "apple": { "url": "https://<wallet>/passes/<cardId>.pkpass?exp=…&sig=…", "expiresAt": "…" }, "google": null }
```
Pedir el código:
- Teléfono a E.164 con libphonenumber (México por defecto; también acepta el antiguo `+52 1`). Si no:
  400 `INVALID_PHONE`. Slug inválido, inexistente o inactivo: 404 `TENANT_NOT_FOUND`.
- Canal: `channel` opcional (`whatsapp` | `sms`, sin distinguir mayúsculas; ausente o vacío = el de por
  defecto). Desconocido o no habilitado: 400 `CHANNEL_NOT_AVAILABLE`, antes de contar cualquier límite.
  La respuesta agrega `channel` y `availableChannels`; un cliente anterior que no manda `channel` sigue igual.
  El error `CHANNEL_NOT_AVAILABLE` agrega `availableChannels` (sin `channel`) y `RECIPIENT_UNREACHABLE` agrega
  `channel` (el intentado) y `availableChannels`. Son campos aditivos: `code` y `message` no cambian y los demás
  errores (incluidos `VERIFICATION_UNAVAILABLE` y `RATE_LIMITED`) siguen siendo solo `{code, message}`.
- Límites por hora (429 `RATE_LIMITED` con `Retry-After`): 3 códigos por teléfono y negocio
  (`ENROLL_PHONE_LIMIT_PER_HOUR`, contado con `phone_verifications.created_at`, con un bloqueo por teléfono
  para que dos solicitudes simultáneas no pasen las dos), 10 por IP (`ENROLL_IP_LIMIT_PER_HOUR`) y 60 por
  negocio (`ENROLL_TENANT_LIMIT_PER_HOUR`). Los de IP y negocio usan `loyalty.enroll_rate_limits` (ventana
  fija de una hora, como `activation_attempts`); la IP va como SHA-256, nunca en claro. Cada solicitud
  cuenta, se acepte o no. **Los límites, la vigencia y los intentos no dependen del canal**: pedir por WhatsApp
  y luego por SMS gasta dos de los 3 lugares del teléfono, y cada código tiene su propia vigencia e intentos
  (cambiar de canal no los reinicia ni los duplica). El canal queda en `phone_verifications.channel` (V6) solo
  para auditoría.
- Código de 6 dígitos (`SecureRandom`), vigencia `ENROLL_CODE_TTL_MINUTES`, 0 intentos. En la base solo
  `code_hash` = HMAC-SHA256 en hex con llave `OTP_HASH_SALT` de `"{tenantId}|{phoneE164}|{código}"`.
- Nunca consulta tarjetas: la respuesta y el tiempo son los mismos tenga o no tarjeta el teléfono.
- Si el envío falla, la verificación creada sigue contando para el límite: 400 `RECIPIENT_UNREACHABLE` si el
  número no puede recibir por ese canal; cualquier otro fallo del proveedor (red, tiempo agotado, 4xx/5xx,
  credenciales) 503 `VERIFICATION_UNAVAILABLE`. La respuesta nunca lleva detalles del proveedor.

Dar la tarjeta:
- Nombre: texto plano de 1 a 80 caracteres (recortado; sin controles, `<`, `>` ni controles bidi), si no
  400 `INVALID_NAME`; teléfono como arriba; código de 6 dígitos (si no, o si `verificationId` no es un
  UUID: 400 `INVALID_CODE`).
- Límite de altas por IP: `ENROLL_CARD_IP_LIMIT_PER_HOUR` (30) → 429 `RATE_LIMITED`.
- La verificación se busca por `verificationId`, negocio y teléfono; cualquier discrepancia es 400
  `INVALID_CODE` (el mismo cuerpo, sin decir cuál). Vencida: 400 `CODE_EXPIRED`. Ya validada: vale hasta
  vencer sin volver a comparar. Si no, suma el intento con un `UPDATE … attempts = attempts + 1` atómico
  **antes** de comparar el hash (tiempo constante); sin coincidencia 400 `INVALID_CODE`, y al llegar a
  `ENROLL_MAX_ATTEMPTS` (5) 400 `TOO_MANY_ATTEMPTS` ("Pide un código nuevo"; sin `Retry-After`).
- Tarjeta en una transacción corta: `INSERT … ON CONFLICT (tenant_id, phone_e164) DO NOTHING`; si ya existía
  se lee y conserva su nombre. `isNew` = si se insertó en esta llamada. Commit antes de llamar al wallet-service.
- `POST {WALLET_SERVICE_URL}/internal/passes` con `{cardId, tenantId, holderName, points}` y un ID token de
  Google de la cuenta de runtime (`sa-loyalty-service`, por el servidor de metadatos) con audiencia
  `WALLET_SERVICE_AUDIENCE`. Tope `WALLET_ISSUE_TIMEOUT_MS` (20 s) para el token y para cada intento; un solo
  reintento si el intento se agota o falla la conexión (IssuePass es idempotente por `cardId`), nunca ante una
  respuesta HTTP. Log INFO con `attempt` y `durationMs`; WARN por intento agotado y por fallo final. 409 `TEMPLATE_NOT_READY` → 409
  `TEMPLATE_NOT_READY`; tiempo agotado, red o 5xx → 503 `PASS_SERVICE_UNAVAILABLE`; 401/403 → 503
  `PASS_SERVICE_UNAVAILABLE` y log ERROR "Authentication to wallet-service was rejected". Sin
  `WALLET_SERVICE_URL`: 503 `PASS_SERVICE_UNAVAILABLE` (la tarjeta sí se crea).
- Reintento seguro: si el wallet-service falla, la tarjeta y la verificación validada se quedan; el
  cliente reintenta con el mismo código hasta que venza (el reintento responde `isNew: false`).
- `google` es `null` hasta conectar Google Wallet.

Envío del código (un puerto, `PhoneVerificationSenderPort`; `VERIFICATION_SENDER` es el canal por defecto y
`VERIFICATION_ENABLED_CHANNELS` la lista de canales disponibles):

| `VERIFICATION_SENDER` | Comportamiento |
|---|---|
| `disabled` (por defecto) | No envía; pedir un código responde 503 `VERIFICATION_UNAVAILABLE` sin crear nada. `VERIFICATION_ENABLED_CHANNELS` se ignora |
| `log` | Reemplaza a todos los canales habilitados (sin credenciales): escribe el canal pedido y el código en un log INFO (`Verification code (VERIFICATION_SENDER=log) tenantId=… verificationId=… channel=sms code=…`). Sin `VERIFICATION_ENABLED_CHANNELS` simula `whatsapp,sms`; el primero es el de por defecto. **Solo con `APP_ENV=dev`**: con otro valor (p. ej. `prod`) o sin `APP_ENV` el servicio no arranca |
| `whatsapp` / `sms` | Canal por defecto. `VERIFICATION_ENABLED_CHANNELS` vacía = solo ese canal; si se define, debe incluirlo |

El servicio **no arranca** (fail fast) si `VERIFICATION_SENDER` o un canal de `VERIFICATION_ENABLED_CHANNELS` es
desconocido, si el canal por defecto no está en la lista, si a un canal habilitado le faltan credenciales
(el mensaje nombra las variables, nunca sus valores) o si una variable de proveedor está mal formada.

WhatsApp (Cloud API de Meta, `WhatsAppPhoneVerificationSender`):
- `POST https://graph.facebook.com/{WHATSAPP_GRAPH_API_VERSION}/{WHATSAPP_PHONE_NUMBER_ID}/messages` con
  `Authorization: Bearer {WHATSAPP_ACCESS_TOKEN}` y la plantilla de autenticación con botón de copiar código
  (`WHATSAPP_TEMPLATE_NAME`, idioma `WHATSAPP_TEMPLATE_LANGUAGE`). El código va dos veces, como pide Meta: en el
  parámetro del body y en el del botón (`sub_type: "url"`, `index: "0"`):
  ```json
  {"messaging_product": "whatsapp", "recipient_type": "individual", "to": "+525512345678", "type": "template",
   "template": {"name": "yiupi_codigo_verificacion", "language": {"code": "es_MX"}, "components": [
     {"type": "body", "parameters": [{"type": "text", "text": "042137"}]},
     {"type": "button", "sub_type": "url", "index": "0", "parameters": [{"type": "text", "text": "042137"}]}]}}
  ```
- Tope `WHATSAPP_TIMEOUT_MS` por intento (conexión: mínimo entre ese tope y 3 s). **Un solo reintento, y solo si
  no se pudo conectar** (conexión rechazada, tiempo de conexión agotado, DNS). Un tiempo agotado después de
  conectar o cualquier respuesta HTTP nunca se reintenta (el mensaje pudo haber salido).
- Error `131026` → 400 `RECIPIENT_UNREACHABLE`; 401/403 o error `190` → log ERROR "Authentication to WhatsApp
  Cloud API was rejected"; otros 4xx → log ERROR; 429/5xx → WARN. Todos menos 131026 → 503.
- Una respuesta 2xx solo significa que Meta aceptó el mensaje. En la práctica Meta suele informar 131026 de
  forma asíncrona (webhook de estados), no en la respuesta del envío; esos casos no se ven aquí.

SMS (Twilio Programmable Messaging, `TwilioSmsPhoneVerificationSender`; **no** Twilio Verify: el código se
genera y valida en este servicio):
- `POST https://api.twilio.com/2010-04-01/Accounts/{TWILIO_ACCOUNT_SID}/Messages.json`, formulario
  `application/x-www-form-urlencoded`, autenticación básica `TWILIO_ACCOUNT_SID:TWILIO_AUTH_TOKEN`; campos
  `To` (E.164), `MessagingServiceSid` (`TWILIO_MESSAGING_SERVICE_SID`, preferido) o `From` (`TWILIO_FROM`, E.164)
  y `Body`.
- Texto sin acentos ni enlaces, en un solo segmento GSM-7 (≤ 160 caracteres):
  `Cafe Luna: tu codigo es 042137. Vence en 10 min. No lo compartas.` El nombre del negocio se translitera a
  ASCII ("Café" → "Cafe"), se filtra a letras, números y espacios y se recorta a 30 caracteres; si queda
  vacío: `Tu codigo es …`.
- Un solo intento con tope `TWILIO_TIMEOUT_MS`, **sin reintentos automáticos** (para no duplicar mensajes).
- Error `21211` → 400 `RECIPIENT_UNREACHABLE`; 401/403 o error `20003` → log ERROR "Authentication to Twilio
  was rejected"; otros 4xx → log ERROR; 429/5xx → WARN. Todos menos 21211 → 503.
- **Un 201 de Twilio solo significa que aceptó el mensaje (`queued`/`accepted`), no que se entregó.** El
  seguimiento de la entrega (`StatusCallback`) no está implementado.

Privacidad: los logs nunca llevan el teléfono (ni enmascarado), el nombre, el código (fuera del modo `log`), el
hash, la IP, el texto del SMS ni los tokens de los proveedores. Para correlacionar un fallo: `tenantId`,
`verificationId`, `channel`, estado HTTP y el código numérico de error del proveedor (`providerCode`), más el
`sid` del mensaje de Twilio. Commands, requests, payloads y propiedades con esos datos sobrescriben `toString`.
`EnrollmentFlowIT`, `EnrollmentLogSenderIT` y las pruebas de contrato de los adaptadores lo verifican.

Pendiente: seguimiento de entrega (webhooks de estados de Meta y `StatusCallback` de Twilio);
limpieza periódica de `phone_verifications` vencidas y de `enroll_rate_limits` (y `activation_attempts`,
`login_attempts`) con ventana vencida; Google Wallet (`google` en `null`).

### Perfil y logo del negocio
- Solo OWNER (CASHIER → 403 `FORBIDDEN`; sin token → 401). El negocio sale del token: las rutas no
  llevan slug ni tenantId, y si el cuerpo o la query los traen se ignoran.
- Cada cambio escribe `TenantChanged` en `loyalty.outbox` en la misma transacción, con la fila del negocio
  bloqueada (`SELECT ... FOR UPDATE`). Un PUT que no cambia nada no escribe ni avisa.
- Logo: máximo 2 MB (413 `LOGO_TOO_LARGE`). Se aceptan PNG, JPEG y WebP según sus primeros bytes
  (magic bytes), nunca por Content-Type ni extensión; SVG y lo demás → 415 `UNSUPPORTED_IMAGE_FORMAT`.
  Ancho y alto se leen de la cabecera antes de decodificar: más de 4000 px por lado → 400
  `IMAGE_DIMENSIONS_TOO_LARGE` (protege contra imágenes bomba). Imagen dañada → 400 `INVALID_IMAGE`.
- Se normaliza a un PNG de 512 × 512 (recorte cuadrado centrado, sin metadatos) y se guarda en el bucket
  como `tenants/{tenantId}/logo-{sha256}.png`, con `Cache-Control: public, max-age=31536000, immutable`.
  El nombre cambia con el contenido, así que la caché se invalida sola. Al reemplazar o quitar el logo se
  borra el objeto anterior después de confirmar el cambio en la base.
- `logoUrl` es la URL pública del objeto (bucket con lectura pública), la misma que devuelve
  `GET /api/public/tenants/{slug}/identity` y la que recibe el wallet-service en `TenantChanged`.

```bash
curl -X PUT "$URL/api/staff/tenant/logo" -H "Authorization: Bearer $ACCESS_TOKEN" -F "file=@logo.webp"
```

### Manifest de la PWA de caja
La PWA (loyalty-web) se instala por negocio: `/caja/{slug}` enlaza `/caja/{slug}/manifest.webmanifest`,
y Firebase Hosting reenvía esa ruta a este servicio con el path original (está fuera de `/api`).
- Público: no lo intercepta ningún filtro (`AdminAuthFilter` solo cubre `/api/admin`; `StaffAuthFilter`,
  `/api/staff` y `/api/auth/me`; `InternalAuthFilter`, `/internal`) y el servicio no usa la cadena de seguridad de Spring WebFlux.
- `Content-Type: application/manifest+json`, `Cache-Control: public, max-age=300`.
- Sale de la identidad pública (`GetTenantIdentityHandler`): no expone nada más. `id` y `start_url` =
  `/caja/{slug}`, `scope` `/`, `short_name` = nombre recortado a 12 caracteres sin partir palabras (si la
  primera ya no cabe, se corta), `lang` `es-MX`, `display` `standalone`, `orientation` `portrait`,
  colores `#f6f4ef`. Íconos: el logo (PNG 512 × 512) declarado como `512x512` y `192x192`; sin logo,
  `/icons/icon-192.png` y `/icons/icon-512.png` de la web.
- Negocio inexistente, inactivo o slug inválido → 404 sin cuerpo.

```json
{ "id": "/caja/mi-cafe", "name": "Cafetería Luna", "short_name": "Cafetería", "description": "Bienvenido",
  "lang": "es-MX", "start_url": "/caja/mi-cafe", "scope": "/", "display": "standalone", "orientation": "portrait",
  "background_color": "#f6f4ef", "theme_color": "#f6f4ef",
  "icons": [ { "src": "https://storage.googleapis.com/<bucket>/tenants/<id>/logo-<sha256>.png", "sizes": "512x512", "type": "image/png", "purpose": "any" },
             { "src": "https://storage.googleapis.com/<bucket>/tenants/<id>/logo-<sha256>.png", "sizes": "192x192", "type": "image/png", "purpose": "any" } ] }
```

### Avisos al wallet-service (outbox → Pub/Sub)
Diseño 12.11. Cada cambio escribe su aviso en `loyalty.outbox` en la misma transacción; publicarlo es
`PublishPendingEventsHandler`, por dos caminos:
- **Camino rápido**: después del commit de crear negocio, cambiar identidad o configuración, y subir o
  quitar el logo, el handler publica en la misma petición el aviso que escribió
  (`OutboxFastPath`), con un máximo de `OUTBOX_FAST_PATH_TIMEOUT_MS`. Si Pub/Sub falla o tarda, el aviso
  queda pendiente y la respuesta de la operación no cambia.
- **Red de seguridad**: Cloud Scheduler llama cada minuto a `POST /internal/outbox/publish` y publica un
  lote de hasta `OUTBOX_BATCH_SIZE` avisos `PENDING` vencidos.

Los dos caminos reclaman igual (`sql/outbox/claim-*.sql`): una sola sentencia con `FOR UPDATE SKIP LOCKED`
que corre el `next_attempt_at` del aviso al fin de un plazo (`OUTBOX_CLAIM_LEASE_SECONDS`). El bloqueo dura
solo esa sentencia, no la llamada a Pub/Sub; mientras dura el plazo ninguna otra ejecución lo toma. Si la
instancia muere a medio publicar, el aviso vuelve a estar disponible al vencer el plazo (puede salir dos
veces: es inofensivo, el wallet-service ignora repetidos por `seq`).
```text
Pub/Sub confirma  → status PUBLISHED, published_at
Pub/Sub falla     → attempts+1, next_attempt_at = ahora + 5 s × 2^(attempts−1) (5 s, 10 s, 20 s, …; tope 15 min)
                    (también si no confirma en OUTBOX_PUBLISH_TIMEOUT_MS)
attempts = OUTBOX_MAX_ATTEMPTS → status FAILED y log ERROR "Outbox event FAILED id=… eventType=… tenantId=…"
```
- Mensaje: cuerpo = `payload` (JSON); atributos `eventType`, `tenantId`, `cardId` (cadena vacía si no hay) y
  `seq` (el `id` del aviso).
- Sin `PUBSUB_TOPIC` la publicación queda desactivada: los avisos se acumulan en `PENDING` y el endpoint
  responde `{"claimed":0,"published":0,"failed":0,"disabled":true}`.
- `/internal/**` lo protege `InternalAuthFilter` con un ID token de Google, igual que la API admin, pero con
  `OUTBOX_PUBLISH_AUDIENCE` y `OUTBOX_PUBLISH_ALLOWED_EMAILS` (la cuenta del job de Scheduler). Sin token o
  token inválido → 401 `UNAUTHORIZED`; otro correo o variables vacías → 403 `FORBIDDEN`.
- Logs: solo conteos e ids; nunca el payload.
- Pendiente: limpieza de avisos `PUBLISHED` antiguos y la alerta sobre los `FAILED`.

### Autenticación de la API admin
Sin secretos compartidos: n8n corre en una VM de GCP y presenta un ID token de Google de su cuenta
de servicio. El servicio valida firma RS256 (llaves de `https://www.googleapis.com/oauth2/v3/certs`),
issuer `https://accounts.google.com`, audiencia igual a `ADMIN_AUDIENCE`, `email_verified = true`
y correo dentro de `ADMIN_ALLOWED_EMAILS`. Si alguna de las dos variables está vacía, la API admin
responde 403 siempre.

Obtener el token desde la VM (dura 1 hora; pedir uno nuevo en cada ejecución del flujo):
```bash
TOKEN=$(curl -s -H "Metadata-Flavor: Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=<ADMIN_AUDIENCE>&format=full")
curl -X POST "<ADMIN_AUDIENCE>/api/admin/tenants" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"slug":"mi-cafe","name":"Mi Café","pointsGoal":10,"rewardText":"Café gratis"}'
```

## Correr en local
```bash
docker compose up -d
DATABASE_URL='postgresql://loyalty:loyalty@localhost:5433/loyalty?sslmode=disable' ./gradlew bootRun
curl localhost:8080/actuator/health/liveness
```
Flyway crea los schemas `loyalty` y `wallet` al arrancar.

## Pruebas
```bash
./gradlew test
```
`TenantIdentityQueryAdapterIT`, `AdminFlowIT`, `AuthFlowIT`, `ActivationFlowIT`, `TenantProfileFlowIT`, `TenantManifestFlowIT`,
`OutboxFlowIT`, `EnrollmentFlowIT`, `EnrollmentLogSenderIT`, `EnrollmentDisabledSenderIT` (el wallet-service es un
servidor HTTP falso, `FakeWalletService`), `GcsLogoStorageIT` (contra `fsouza/fake-gcs-server`) y `PubSubEventAdapterIT` (contra el emulador de
Pub/Sub, `google-cloud-cli:583.0.0-emulators`) usan Testcontainers (requieren Docker; se omiten si no hay).
Los WebP de prueba (`src/test/resources/images`) se generaron con `cwebp`: el JDK no escribe WebP.

## Configuración
| Variable | Descripción | Por defecto |
|---|---|---|
| `DATABASE_URL` | Cadena `postgresql://usuario:clave@host/base?sslmode=require` (viene de Secret Manager: `neon-database-url`) | obligatoria |
| `DB_POOL_MAX_SIZE` | Conexiones máximas del pool R2DBC | 10 |
| `DB_MIGRATE_ON_STARTUP` | Ejecutar Flyway al arrancar | true |
| `PORT` | Puerto HTTP (lo fija Cloud Run) | 8080 |
| `ADMIN_AUDIENCE` | Audiencia esperada en el ID token de la API admin (URL del servicio). Dev: `https://loyalty-service-1030546411821.us-east4.run.app` | vacía (API admin cerrada) |
| `ADMIN_ALLOWED_EMAILS` | Correos permitidos en la API admin, separados por comas. Dev: `axioma-n8n-dev-sa@project-9c6fd1d6-847b-4bb0-919.iam.gserviceaccount.com` | vacía (API admin cerrada) |
| `JWT_SIGNING_KEY` | Clave HS256 de los access tokens, 32+ bytes (Secret Manager: `jwt-signing-key`). Sin ella el servicio arranca pero el login responde 503 | vacía |
| `ACCESS_TOKEN_TTL_SECONDS` | Vigencia del access token | 900 |
| `REFRESH_TOKEN_TTL_DAYS` | Vigencia de la sesión desde el último refresh | 30 |
| `LOGIN_MAX_ATTEMPTS` | Fallos por (negocio, usuario) antes de bloquear | 5 |
| `LOGIN_WINDOW_MINUTES` | Ventana de los fallos y duración del bloqueo | 15 |
| `APP_WEB_BASE_URL` | URL de la PWA para armar `activationUrl`. Dev: `https://axioma-loyalty-dev-web.web.app`. La pone el despliegue (`ci.yml`) desde la variable de repositorio `vars.APP_WEB_BASE_URL`. Sin ella la API admin devuelve solo `token` y `expiresAt` | vacía |
| `ACTIVATION_TOKEN_TTL_HOURS` | Vigencia de un enlace de activación | 72 |
| `ACTIVATION_MAX_ATTEMPTS_PER_IP` | Fallos de activación por (slug, IP) antes de bloquear | 5 |
| `ACTIVATION_MAX_ATTEMPTS_PER_SLUG` | Fallos de activación por slug antes de bloquear | 20 |
| `ACTIVATION_WINDOW_MINUTES` | Ventana de esos fallos y duración del bloqueo | 15 |
| `PASSWORD_MIN_LENGTH` | Largo mínimo de contraseña (máximo fijo: 72 bytes, límite de bcrypt) | 8 |
| `BCRYPT_STRENGTH` | Costo de bcrypt | 12 |
| `LOGOS_BUCKET` | Bucket de logos (Terraform: `<project_id>-logos`; lo pone en Cloud Run). Sin él, subir un logo responde 503 `STORAGE_UNAVAILABLE` | vacía |
| `LOGOS_PUBLIC_BASE_URL` | Base de las URLs públicas de los logos (p. ej. un CDN) | `https://storage.googleapis.com/<LOGOS_BUCKET>` |
| `PUBSUB_TOPIC` | Tópico completo de los avisos. Dev: `projects/axioma-loyalty-dev/topics/loyalty-events`. Sin él la publicación queda desactivada; con un valor mal formado el servicio no arranca | vacía (desactivada) |
| `OUTBOX_BATCH_SIZE` | Avisos por ejecución de la red de seguridad (1–500) | 50 |
| `OUTBOX_MAX_ATTEMPTS` | Intentos fallidos antes de pasar a `FAILED` | 8 |
| `OUTBOX_FAST_PATH_TIMEOUT_MS` | Tiempo máximo del camino rápido dentro de la petición | 1000 |
| `OUTBOX_PUBLISH_TIMEOUT_MS` | Tiempo máximo para que Pub/Sub confirme un aviso (incluye los reintentos del cliente) | 10000 |
| `OUTBOX_CLAIM_LEASE_SECONDS` | Plazo de un aviso reclamado; debe pasar de `OUTBOX_PUBLISH_TIMEOUT_MS` | 120 |
| `OUTBOX_PUBLISH_AUDIENCE` | Audiencia del ID token de Cloud Scheduler (la que envía el job). Dev: `https://loyalty-service-g42hkr2xmq-uk.a.run.app` | vacía (`/internal` cerrado) |
| `OUTBOX_PUBLISH_ALLOWED_EMAILS` | Correos permitidos en `/internal`, separados por comas. Dev: `sa-scheduler@axioma-loyalty-dev.iam.gserviceaccount.com` | vacía (`/internal` cerrado) |
| `OTP_HASH_SALT` | Llave del HMAC de los códigos, 32+ bytes (Secret Manager: `otp-hmac-salt`). Sin ella pedir un código o la tarjeta responde 503 `VERIFICATION_UNAVAILABLE` | vacía |
| `VERIFICATION_SENDER` | Canal por defecto del código: `disabled`, `log` (solo con `APP_ENV=dev`), `whatsapp` o `sms`. Otro valor: no arranca | `disabled` |
| `VERIFICATION_ENABLED_CHANNELS` | Canales disponibles, separados por comas (`whatsapp,sms`); el orden es el de `availableChannels`. Debe incluir el de `VERIFICATION_SENDER`. Canal desconocido: no arranca | vacía (solo el de `VERIFICATION_SENDER`; con `log`, ambos) |
| `APP_ENV` | Ambiente. Solo `dev` permite `VERIFICATION_SENDER=log` | vacía |
| `WHATSAPP_ACCESS_TOKEN` | **Secreto** (Secret Manager). Token de la app de Meta para la Cloud API. Obligatorio si `whatsapp` está habilitado | vacía |
| `WHATSAPP_PHONE_NUMBER_ID` | Id numérico del número de WhatsApp Business que envía. Obligatorio si `whatsapp` está habilitado | vacía |
| `WHATSAPP_TEMPLATE_NAME` | Plantilla de autenticación aprobada por Meta (minúsculas, números y `_`) | `yiupi_codigo_verificacion` |
| `WHATSAPP_TEMPLATE_LANGUAGE` | Idioma de la plantilla (`es`, `es_MX`) | `es_MX` |
| `WHATSAPP_GRAPH_API_VERSION` | Versión de la Graph API (`vNN.N`) | `v26.0` |
| `WHATSAPP_TIMEOUT_MS` | Tope de cada intento (1–30000); un reintento solo si no hubo conexión | 5000 |
| `WHATSAPP_API_BASE_URL` | Base de la Graph API (solo para pruebas) | `https://graph.facebook.com` |
| `TWILIO_ACCOUNT_SID` | Cuenta de Twilio (`AC` + 32 hex). Obligatoria si `sms` está habilitado | vacía |
| `TWILIO_AUTH_TOKEN` | **Secreto** (Secret Manager). Auth token de la cuenta. Obligatorio si `sms` está habilitado | vacía |
| `TWILIO_MESSAGING_SERVICE_SID` | Messaging Service (`MG` + 32 hex), remitente preferido. Con `sms` habilitado se necesita este o `TWILIO_FROM` | vacía |
| `TWILIO_FROM` | Número remitente en E.164; solo se usa sin `TWILIO_MESSAGING_SERVICE_SID` | vacía |
| `TWILIO_TIMEOUT_MS` | Tope del único intento (1–30000); sin reintentos | 5000 |
| `TWILIO_API_BASE_URL` | Base de la API de Twilio (solo para pruebas) | `https://api.twilio.com` |
| `ENROLL_PHONE_LIMIT_PER_HOUR` | Códigos por (negocio, teléfono) en la última hora | 3 |
| `ENROLL_IP_LIMIT_PER_HOUR` | Solicitudes de código por IP por hora | 10 |
| `ENROLL_TENANT_LIMIT_PER_HOUR` | Solicitudes de código por negocio por hora | 60 |
| `ENROLL_CARD_IP_LIMIT_PER_HOUR` | Altas (`/enroll/card`) por IP por hora | 30 |
| `ENROLL_CODE_TTL_MINUTES` | Vigencia del código (1–60) | 10 |
| `ENROLL_MAX_ATTEMPTS` | Intentos por código (1–20) | 5 |
| `WALLET_SERVICE_URL` | Base del wallet-service. Dev: `https://wallet-service-g42hkr2xmq-uk.a.run.app`. Sin ella el alta responde 503 `PASS_SERVICE_UNAVAILABLE` | vacía |
| `WALLET_SERVICE_AUDIENCE` | Audiencia del ID token hacia el wallet-service (su `WALLET_EVENTS_AUDIENCE`). Dev: `https://wallet-service-g42hkr2xmq-uk.a.run.app` | `WALLET_SERVICE_URL` |
| `WALLET_ISSUE_TIMEOUT_MS` | Tope de obtener el token y de cada intento de IssuePass (un reintento solo por tiempo agotado o conexión fallida). Cubre un arranque en frío | 20000 |

Cloud Storage, Pub/Sub y el ID token hacia el wallet-service usan Application Default Credentials (en Cloud Run, la cuenta de servicio del servicio; sin llaves).
En local no hace falta para Storage (el cliente se crea al primer uso); con `PUBSUB_TOPIC` definido, el publisher
necesita ADC al arrancar.

`DATABASE_URL` también acepta la cadena copiada como comando de Neon (`psql 'postgresql://...'`).

Si las tablas ya existen sin historial de Flyway (la base de pruebas de Neon se creó a mano con
el mismo esquema), Flyway las toma como línea base en la versión 1.

## Despliegue
GitHub Actions (`.github/workflows/ci.yml`): prueba en cada PR; al hacer merge a `main` construye
la imagen, la sube a Artifact Registry y despliega en Cloud Run (dev) con Workload Identity Federation.
Variables del repo: `GCP_PROJECT_ID`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOYER_SA`
(salen de `terraform output` en `loyalty-infra`), y `ADMIN_AUDIENCE`, `ADMIN_ALLOWED_EMAILS`, `APP_WEB_BASE_URL`,
`PUBSUB_TOPIC`, `OUTBOX_BATCH_SIZE`, `OUTBOX_MAX_ATTEMPTS`, `OUTBOX_FAST_PATH_TIMEOUT_MS`, `OUTBOX_PUBLISH_AUDIENCE`,
`OUTBOX_PUBLISH_ALLOWED_EMAILS`, `VERIFICATION_SENDER`, `VERIFICATION_ENABLED_CHANNELS`, `APP_ENV`, los `WHATSAPP_*` y
`TWILIO_*` que no son secretos, `WALLET_SERVICE_URL`, `WALLET_SERVICE_AUDIENCE`, `WALLET_ISSUE_TIMEOUT_MS` y `ENROLL_*`
(se pasan a Cloud Run como variables de entorno; no son secretos). Una variable
numérica sin definir llega vacía y toma su valor por defecto. Secretos de Secret Manager: `DATABASE_URL`
(`neon-database-url`), `JWT_SIGNING_KEY` (`jwt-signing-key`) y `OTP_HASH_SALT` (`otp-hmac-salt`); cada uno necesita
al menos una versión o el despliegue falla. `WHATSAPP_ACCESS_TOKEN` y `TWILIO_AUTH_TOKEN` también van en Secret
Manager; se cablean aparte en `loyalty-infra` y en el despliegue antes de habilitar `whatsapp` o `sms` (sin ellos,
con el canal habilitado, el servicio no arranca).

## Estructura
```
application/{command,query,handler,validator}
domain/{model,port/out}
adapter/in/web{,/admin,/auth,/staff,/internal,/enroll}  ·  adapter/out/persist/{command,query}  ·  adapter/out/{security,image,storage,messaging,verification,wallet}
config
resources/{db/migration, sql}
```

# loyalty-service

Servicio del programa de lealtad: negocios, identidad, cuentas, sesiones, tarjetas y puntos.
Java 21, Spring Boot + WebFlux, R2DBC, Gradle Kotlin DSL, arquitectura hexagonal
(estándar de desarrollo de Axioma v1.0). Base de datos: Neon (schema `loyalty`).

## Estado (entrega 1, avance 4)
- Esqueleto hexagonal, errores normalizados, `requestId` en logs, migraciones Flyway.
- `GET /api/public/tenants/{slug}/identity`: identidad pública del negocio.
- API admin para n8n: alta de negocios y cuentas del personal (protegida con ID tokens de Google).
- Login, sesiones (access + refresh con rotación) y roles del personal.
- Perfil del negocio para el dueño: identidad, configuración del programa y logo (Cloud Storage).
- Manifest de la PWA de caja por negocio (`/caja/{slug}/manifest.webmanifest`).
- Activación de cuentas por enlace de un solo uso (la persona elige su contraseña).

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
Errores: `{ "code": "...", "message": "..." }`. 400 `INVALID_SLUG`, `INVALID_TEXT`,
`INVALID_BRAND_COLOR`, `INVALID_SETTINGS`, `INVALID_USER`, `WEAK_PASSWORD`, `INVALID_REQUEST`, `INVALID_ACTIVATION`,
`INVALID_IMAGE`, `IMAGE_DIMENSIONS_TOO_LARGE`; 401 `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_SESSION`;
403 `FORBIDDEN`; 404 `TENANT_NOT_FOUND`, `USER_NOT_FOUND`; 409 `SLUG_ALREADY_EXISTS`, `USERNAME_TAKEN`, `USER_INACTIVE`;
413 `LOGO_TOO_LARGE`; 415 `UNSUPPORTED_IMAGE_FORMAT`; 429 `TOO_MANY_ATTEMPTS` (con `Retry-After`);
500 `INTERNAL_ERROR`; 503 `AUTH_UNAVAILABLE`, `STORAGE_UNAVAILABLE`.

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
  `/api/staff` y `/api/auth/me`) y el servicio no usa la cadena de seguridad de Spring WebFlux.
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
`TenantIdentityQueryAdapterIT`, `AdminFlowIT`, `AuthFlowIT`, `ActivationFlowIT`, `TenantProfileFlowIT`, `TenantManifestFlowIT` y `GcsLogoStorageIT`
(contra `fsouza/fake-gcs-server`) usan Testcontainers (requieren Docker; se omiten si no hay).
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

Cloud Storage usa Application Default Credentials (en Cloud Run, la cuenta de servicio del servicio; sin llaves).
En local no hace falta: el cliente se crea al primer uso.

`DATABASE_URL` también acepta la cadena copiada como comando de Neon (`psql 'postgresql://...'`).

Si las tablas ya existen sin historial de Flyway (la base de pruebas de Neon se creó a mano con
el mismo esquema), Flyway las toma como línea base en la versión 1.

## Despliegue
GitHub Actions (`.github/workflows/ci.yml`): prueba en cada PR; al hacer merge a `main` construye
la imagen, la sube a Artifact Registry y despliega en Cloud Run (dev) con Workload Identity Federation.
Variables del repo: `GCP_PROJECT_ID`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOYER_SA`
(salen de `terraform output` en `loyalty-infra`), y `ADMIN_AUDIENCE`, `ADMIN_ALLOWED_EMAILS`
(se pasan a Cloud Run como variables de entorno; no son secretos).

## Estructura
```
application/{command,query,handler,validator}
domain/{model,port/out}
adapter/in/web{,/admin,/auth,/staff}  ·  adapter/out/persist/{command,query}  ·  adapter/out/{security,image,storage}
config
resources/{db/migration, sql}
```

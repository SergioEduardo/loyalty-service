# loyalty-service

Servicio del programa de lealtad: negocios, identidad, cuentas, sesiones, tarjetas y puntos.
Java 21, Spring Boot + WebFlux, R2DBC, Gradle Kotlin DSL, arquitectura hexagonal
(estándar de desarrollo de Axioma v1.0). Base de datos: Neon (schema `loyalty`).

## Estado (entrega 1, avance 3)
- Esqueleto hexagonal, errores normalizados, `requestId` en logs, migraciones Flyway.
- `GET /api/public/tenants/{slug}/identity`: identidad pública del negocio.
- API admin para n8n: alta de negocios y cuentas del personal (protegida con ID tokens de Google).
- Login, sesiones (access + refresh con rotación) y roles del personal.

## Endpoints
| Método y ruta | Respuesta | Descripción |
|---|---|---|
| `GET /api/public/tenants/{slug}/identity` | 200 | Identidad pública del negocio (sin autenticación) |
| `POST /api/admin/tenants` | 201 | Alta de negocio y aviso `TenantChanged` en `loyalty.outbox` |
| `POST /api/admin/tenants/{tenantId}/users` | 201 | Alta de cuenta (`CASHIER` u `OWNER`) |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/deactivate` | 204 | Desactiva la cuenta y revoca sus sesiones |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/reset-password` | 204 | Cambia la contraseña y revoca sus sesiones |
| `POST /api/auth/login` | 200 | Inicio de sesión del personal: `{slug, username, password}` |
| `POST /api/auth/refresh` | 200 | Renueva la sesión: `{refreshToken}`; devuelve un refresh nuevo |
| `POST /api/auth/logout` | 204 | Cierra la sesión: `{refreshToken}`; idempotente |
| `GET /api/auth/me` | 200 | Quién soy, según el access token: `{userId, tenantId, role, sessionId}` |

Cuerpos (JSON):
```jsonc
// POST /api/admin/tenants  (cooldownSeconds default 0, undoWindowSeconds default 300)
{ "slug": "mi-cafe", "name": "Mi Café", "description": "Bienvenido", "brandColor": "#112233",
  "pointsGoal": 10, "rewardText": "Café gratis", "cooldownSeconds": 0, "undoWindowSeconds": 300 }
// POST .../users  (username se guarda recortado y en minúsculas; name opcional, default = username)
{ "username": "ana", "password": "al-menos-8", "role": "CASHIER", "name": "Ana" }
// POST .../reset-password
{ "password": "nueva-clave" }
```
Errores: `{ "code": "...", "message": "..." }`. 400 `INVALID_SLUG`, `INVALID_TEXT`,
`INVALID_BRAND_COLOR`, `INVALID_SETTINGS`, `INVALID_USER`, `WEAK_PASSWORD`, `INVALID_REQUEST`;
401 `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_SESSION`; 403 `FORBIDDEN`;
404 `TENANT_NOT_FOUND`, `USER_NOT_FOUND`; 409 `SLUG_ALREADY_EXISTS`, `USERNAME_TAKEN`;
429 `TOO_MANY_ATTEMPTS` (con `Retry-After`); 500 `INTERNAL_ERROR`; 503 `AUTH_UNAVAILABLE`.

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
`TenantIdentityQueryAdapterIT`, `AdminFlowIT` y `AuthFlowIT` usan Testcontainers (requieren Docker; se omiten si no hay).

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
| `PASSWORD_MIN_LENGTH` | Largo mínimo de contraseña (máximo fijo: 72 bytes, límite de bcrypt) | 8 |
| `BCRYPT_STRENGTH` | Costo de bcrypt | 12 |

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
adapter/in/web{,/admin,/auth}  ·  adapter/out/persist/{command,query}  ·  adapter/out/security
config
resources/{db/migration, sql}
```

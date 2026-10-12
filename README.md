# loyalty-service

Servicio del programa de lealtad: negocios, identidad, cuentas, sesiones, tarjetas y puntos.
Java 21, Spring Boot + WebFlux, R2DBC, Gradle Kotlin DSL, arquitectura hexagonal
(estándar de desarrollo de Axioma v1.0). Base de datos: Neon (schema `loyalty`).

## Estado (entrega 1, avance 2)
- Esqueleto hexagonal, errores normalizados, `requestId` en logs, migraciones Flyway.
- `GET /api/public/tenants/{slug}/identity`: identidad pública del negocio.
- API admin para n8n: alta de negocios y cuentas del personal (protegida con ID tokens de Google).
- Pendiente de la entrega 1: login y sesiones.

## Endpoints
| Método y ruta | Respuesta | Descripción |
|---|---|---|
| `GET /api/public/tenants/{slug}/identity` | 200 | Identidad pública del negocio (sin autenticación) |
| `POST /api/admin/tenants` | 201 | Alta de negocio y aviso `TenantChanged` en `loyalty.outbox` |
| `POST /api/admin/tenants/{tenantId}/users` | 201 | Alta de cuenta (`CASHIER` u `OWNER`) |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/deactivate` | 204 | Desactiva la cuenta y revoca sus sesiones |
| `POST /api/admin/tenants/{tenantId}/users/{userId}/reset-password` | 204 | Cambia la contraseña y revoca sus sesiones |

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
401 `UNAUTHORIZED`; 403 `FORBIDDEN`; 404 `TENANT_NOT_FOUND`, `USER_NOT_FOUND`;
409 `SLUG_ALREADY_EXISTS`, `USERNAME_TAKEN`; 500 `INTERNAL_ERROR`.

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
`TenantIdentityQueryAdapterIT` y `AdminFlowIT` usan Testcontainers (requieren Docker; se omiten si no hay).

## Configuración
| Variable | Descripción | Por defecto |
|---|---|---|
| `DATABASE_URL` | Cadena `postgresql://usuario:clave@host/base?sslmode=require` (viene de Secret Manager: `neon-database-url`) | obligatoria |
| `DB_POOL_MAX_SIZE` | Conexiones máximas del pool R2DBC | 10 |
| `DB_MIGRATE_ON_STARTUP` | Ejecutar Flyway al arrancar | true |
| `PORT` | Puerto HTTP (lo fija Cloud Run) | 8080 |
| `ADMIN_AUDIENCE` | Audiencia esperada en el ID token de la API admin (URL del servicio). Dev: `https://loyalty-service-1030546411821.us-east4.run.app` | vacía (API admin cerrada) |
| `ADMIN_ALLOWED_EMAILS` | Correos permitidos en la API admin, separados por comas. Dev: `axioma-n8n-dev-sa@project-9c6fd1d6-847b-4bb0-919.iam.gserviceaccount.com` | vacía (API admin cerrada) |
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
adapter/in/web{,/admin}  ·  adapter/out/persist/{command,query}  ·  adapter/out/security
config
resources/{db/migration, sql}
```

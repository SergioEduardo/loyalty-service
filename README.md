# loyalty-service

Servicio del programa de lealtad: negocios, identidad, cuentas, sesiones, tarjetas y puntos.
Java 21, Spring Boot + WebFlux, R2DBC, Gradle Kotlin DSL, arquitectura hexagonal
(estándar de desarrollo de Axioma v1.0). Base de datos: Neon (schema `loyalty`).

## Estado (entrega 1, avance 1)
- Esqueleto hexagonal, errores normalizados, `requestId` en logs, migraciones Flyway.
- `GET /api/public/tenants/{slug}/identity`: identidad pública del negocio.
- Pendiente de la entrega 1: administración (alta de negocio y cuentas), login y sesiones.

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
`TenantIdentityQueryAdapterIT` usa Testcontainers (requiere Docker; se omite si no hay).

## Configuración
| Variable | Descripción | Por defecto |
|---|---|---|
| `DATABASE_URL` | Cadena `postgresql://usuario:clave@host/base?sslmode=require` (viene de Secret Manager: `neon-database-url`) | obligatoria |
| `DB_POOL_MAX_SIZE` | Conexiones máximas del pool R2DBC | 10 |
| `DB_MIGRATE_ON_STARTUP` | Ejecutar Flyway al arrancar | true |
| `PORT` | Puerto HTTP (lo fija Cloud Run) | 8080 |

Si las tablas ya existen sin historial de Flyway (la base de pruebas de Neon se creó a mano con
el mismo esquema), Flyway las toma como línea base en la versión 1.

## Despliegue
GitHub Actions (`.github/workflows/ci.yml`): prueba en cada PR; al hacer merge a `main` construye
la imagen, la sube a Artifact Registry y despliega en Cloud Run (dev) con Workload Identity Federation.
Variables del repo: `GCP_PROJECT_ID`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOYER_SA`
(salen de `terraform output` en `loyalty-infra`).

## Estructura
```
application/{command,query,handler,validator}
domain/{model,port/out}
adapter/in/web  ·  adapter/out/persist/{command,query}
config
resources/{db/migration, sql}
```

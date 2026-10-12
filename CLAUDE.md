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

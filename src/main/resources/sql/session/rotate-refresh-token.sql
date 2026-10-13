-- Rota el token de una sesion vigente de una cuenta y negocio activos, y guarda el
-- hash anterior en el historial. Una sola sentencia: atomica sin transaccion explicita.
WITH rotated AS (
    UPDATE loyalty.sessions s
       SET previous_refresh_token_hash = s.refresh_token_hash,
           refresh_token_hash = :newHash,
           expires_at = :expiresAt,
           last_used_at = NOW()
      FROM loyalty.users u, loyalty.tenants t
     WHERE s.refresh_token_hash = :oldHash
       AND s.revoked_at IS NULL
       AND s.expires_at > NOW()
       AND u.tenant_id = s.tenant_id AND u.id = s.user_id AND u.is_active
       AND t.id = s.tenant_id AND t.is_active
 RETURNING s.id AS session_id, s.tenant_id, s.user_id, u.name AS user_name, u.role,
           t.slug, t.business_name
), history AS (
    INSERT INTO loyalty.session_rotated_tokens (token_hash, session_id)
    SELECT :oldHash, session_id FROM rotated
)
SELECT session_id, tenant_id, user_id, user_name, role, slug, business_name
  FROM rotated

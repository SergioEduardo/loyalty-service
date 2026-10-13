-- Marca el enlace como usado si sigue siendo valido. Una sola sentencia: si dos peticiones
-- llegan a la vez, solo una lo consume.
UPDATE loyalty.activation_tokens a
   SET used_at = NOW()
  FROM loyalty.users u, loyalty.tenants t
 WHERE a.token_hash = :tokenHash
   AND a.used_at IS NULL
   AND a.expires_at > NOW()
   AND u.tenant_id = a.tenant_id AND u.id = a.user_id AND u.is_active
   AND t.id = a.tenant_id AND t.is_active AND t.slug = :slug
RETURNING a.tenant_id, a.user_id, t.slug, t.business_name, u.username

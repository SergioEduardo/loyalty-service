-- Enlace sin usar ni vencer, del negocio del slug, con cuenta y negocio activos.
SELECT a.tenant_id, a.user_id, t.slug, t.business_name, u.username
  FROM loyalty.activation_tokens a
  JOIN loyalty.users u ON u.tenant_id = a.tenant_id AND u.id = a.user_id
  JOIN loyalty.tenants t ON t.id = a.tenant_id
 WHERE a.token_hash = :tokenHash
   AND a.used_at IS NULL
   AND a.expires_at > NOW()
   AND t.slug = :slug
   AND t.is_active
   AND u.is_active

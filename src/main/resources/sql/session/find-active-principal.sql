-- La sesion del access token sigue vigente y la cuenta y el negocio siguen activos. Una cuenta
-- sin credencial (pendiente de activacion) no tiene sesiones validas.
SELECT u.role
  FROM loyalty.sessions s
  JOIN loyalty.users u ON u.tenant_id = s.tenant_id AND u.id = s.user_id
  JOIN loyalty.tenants t ON t.id = s.tenant_id
 WHERE s.id = :sessionId
   AND s.tenant_id = :tenantId
   AND s.user_id = :userId
   AND s.revoked_at IS NULL
   AND s.expires_at > NOW()
   AND u.is_active
   AND (u.credential_hash IS NOT NULL OR u.google_subject IS NOT NULL)
   AND t.is_active

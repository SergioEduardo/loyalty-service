UPDATE loyalty.sessions
   SET revoked_at = NOW()
 WHERE tenant_id = :tenantId
   AND user_id = :userId
   AND revoked_at IS NULL

DELETE FROM loyalty.login_attempts
 WHERE tenant_id = :tenantId
   AND username = :username

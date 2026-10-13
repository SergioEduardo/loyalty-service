SELECT count(*)::int AS requests,
       COALESCE(GREATEST(1, CEIL(EXTRACT(EPOCH FROM
           (MIN(created_at) + make_interval(mins => :windowMinutes) - NOW())))), 0)::int AS retry_after_seconds
  FROM loyalty.phone_verifications
 WHERE tenant_id = :tenantId
   AND phone_e164 = :phone
   AND created_at > NOW() - make_interval(mins => :windowMinutes)

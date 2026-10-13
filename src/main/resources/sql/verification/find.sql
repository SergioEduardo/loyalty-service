SELECT id,
       attempts,
       verified_at IS NOT NULL AS verified,
       expires_at <= NOW()     AS expired
  FROM loyalty.phone_verifications
 WHERE id = :id
   AND tenant_id = :tenantId
   AND phone_e164 = :phone

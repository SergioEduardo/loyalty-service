SELECT id, tenant_id, holder_name, points
  FROM loyalty.cards
 WHERE tenant_id = :tenantId
   AND phone_e164 = :phone

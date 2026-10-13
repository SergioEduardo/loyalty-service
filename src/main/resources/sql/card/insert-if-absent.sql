INSERT INTO loyalty.cards (tenant_id, phone_e164, holder_name)
VALUES (:tenantId, :phone, :holderName)
ON CONFLICT (tenant_id, phone_e164) DO NOTHING
RETURNING id, tenant_id, holder_name, points

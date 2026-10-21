INSERT INTO loyalty.phone_verifications (tenant_id, phone_e164, code_hash, channel, expires_at)
VALUES (:tenantId, :phone, :codeHash, :channel, NOW() + make_interval(mins => :ttlMinutes))
RETURNING id, expires_at

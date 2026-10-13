INSERT INTO loyalty.activation_tokens (tenant_id, user_id, token_hash, expires_at)
VALUES (:tenantId, :userId, :tokenHash, :expiresAt)

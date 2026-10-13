-- Los enlaces sin usar de la cuenta dejan de servir.
UPDATE loyalty.activation_tokens
   SET used_at = NOW()
 WHERE tenant_id = :tenantId
   AND user_id = :userId
   AND used_at IS NULL

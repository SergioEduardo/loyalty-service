-- Suma el intento antes de comparar el codigo: dos intentos simultaneos no pueden pasar del maximo.
UPDATE loyalty.phone_verifications
   SET attempts = attempts + 1
 WHERE id = :id
   AND tenant_id = :tenantId
   AND phone_e164 = :phone
   AND verified_at IS NULL
   AND attempts < :maxAttempts
   AND expires_at > NOW()
RETURNING attempts, code_hash

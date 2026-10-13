UPDATE loyalty.phone_verifications
   SET verified_at = NOW()
 WHERE id = :id
   AND verified_at IS NULL

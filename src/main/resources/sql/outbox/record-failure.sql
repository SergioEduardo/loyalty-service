-- Falla al publicar: guarda los intentos y la proxima fecha (espera creciente), o FAILED.
UPDATE loyalty.outbox
   SET attempts = :attempts,
       status = :status,
       next_attempt_at = NOW() + make_interval(secs => :delaySeconds)
 WHERE id = :id
   AND status = 'PENDING'

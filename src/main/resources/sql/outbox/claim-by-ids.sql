-- Igual que claim-due.sql, pero solo con los avisos indicados (camino rapido despues del commit).
WITH due AS MATERIALIZED (
    SELECT id
      FROM loyalty.outbox
     WHERE id = ANY(:ids)
       AND status = 'PENDING'
       AND next_attempt_at <= NOW()
     ORDER BY id
       FOR UPDATE SKIP LOCKED
)
UPDATE loyalty.outbox o
   SET next_attempt_at = NOW() + make_interval(secs => :leaseSeconds)
  FROM due
 WHERE o.id = due.id
RETURNING o.id, o.tenant_id, o.card_id, o.event_type, o.payload::text AS payload, o.attempts

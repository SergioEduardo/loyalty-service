-- Reclama un lote de avisos vencidos: corre su next_attempt_at al fin del plazo para que otra
-- ejecucion no los tome mientras se publican. SKIP LOCKED salta los que otra ejecucion esta
-- reclamando en este momento. Una sola sentencia: la transaccion (y el bloqueo) dura solo el
-- reclamo, no la llamada a Pub/Sub. MATERIALIZED: la subconsulta se evalua una sola vez.
WITH due AS MATERIALIZED (
    SELECT id
      FROM loyalty.outbox
     WHERE status = 'PENDING'
       AND next_attempt_at <= NOW()
     ORDER BY id
     LIMIT :limit
       FOR UPDATE SKIP LOCKED
)
UPDATE loyalty.outbox o
   SET next_attempt_at = NOW() + make_interval(secs => :leaseSeconds)
  FROM due
 WHERE o.id = due.id
RETURNING o.id, o.tenant_id, o.card_id, o.event_type, o.payload::text AS payload, o.attempts

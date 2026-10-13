UPDATE loyalty.outbox
   SET status = 'PUBLISHED',
       published_at = NOW()
 WHERE id = :id
   AND status = 'PENDING'

INSERT INTO loyalty.enroll_rate_limits AS r (limit_key, hits, window_started_at)
VALUES (:key, 1, NOW())
ON CONFLICT (limit_key) DO UPDATE
   SET hits = CASE WHEN r.window_started_at <= NOW() - make_interval(mins => :windowMinutes)
                   THEN 1 ELSE r.hits + 1 END,
       window_started_at = CASE WHEN r.window_started_at <= NOW() - make_interval(mins => :windowMinutes)
                                THEN NOW() ELSE r.window_started_at END
RETURNING hits,
          GREATEST(1, CEIL(EXTRACT(EPOCH FROM (window_started_at + make_interval(mins => :windowMinutes) - NOW()))))::int
              AS retry_after_seconds

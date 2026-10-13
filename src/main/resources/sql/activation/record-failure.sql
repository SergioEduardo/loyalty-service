INSERT INTO loyalty.activation_attempts AS a (attempt_key, failures, window_started_at)
VALUES (:key, 1, NOW())
ON CONFLICT (attempt_key) DO UPDATE
   SET failures = CASE WHEN a.window_started_at <= NOW() - make_interval(mins => :windowMinutes)
                       THEN 1 ELSE a.failures + 1 END,
       window_started_at = CASE WHEN a.window_started_at <= NOW() - make_interval(mins => :windowMinutes)
                                THEN NOW() ELSE a.window_started_at END
RETURNING failures,
          GREATEST(1, CEIL(EXTRACT(EPOCH FROM (window_started_at + make_interval(mins => :windowMinutes) - NOW()))))::int
              AS retry_after_seconds

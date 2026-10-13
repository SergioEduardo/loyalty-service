SELECT failures,
       GREATEST(1, CEIL(EXTRACT(EPOCH FROM (window_started_at + make_interval(mins => :windowMinutes) - NOW()))))::int
           AS retry_after_seconds
  FROM loyalty.activation_attempts
 WHERE attempt_key = :key
   AND window_started_at > NOW() - make_interval(mins => :windowMinutes)

-- Revoca la sesion a la que pertenece el token, sea el vigente o uno ya rotado.
UPDATE loyalty.sessions
   SET revoked_at = NOW()
 WHERE revoked_at IS NULL
   AND id IN (SELECT id FROM loyalty.sessions WHERE refresh_token_hash = :hash
              UNION
              SELECT session_id FROM loyalty.session_rotated_tokens WHERE token_hash = :hash)

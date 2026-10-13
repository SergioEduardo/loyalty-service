-- =====================================================================
-- Inicio de sesion del personal: intentos fallidos y rotacion de tokens.
-- =====================================================================

-- Intentos fallidos por (negocio, usuario), aunque el usuario no exista, para
-- limitar la adivinacion de contrasenas. Una fila por par; la ventana empieza
-- con el primer fallo y se reinicia al vencer. Un inicio de sesion correcto
-- borra la fila. (users.failed_attempts / locked_until quedan sin uso.)
CREATE TABLE loyalty.login_attempts (
    tenant_id          UUID         NOT NULL REFERENCES loyalty.tenants(id),
    username           VARCHAR(60)  NOT NULL,
    failures           INT          NOT NULL CHECK (failures > 0),
    window_started_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, username)
);
-- Para limpiar ventanas vencidas periodicamente.
CREATE INDEX idx_login_attempts_window ON loyalty.login_attempts (window_started_at);

-- Hash de cada token de renovacion ya rotado. Si vuelve a llegar uno de estos,
-- alguien reutiliza un token viejo: se revoca la sesion completa (la familia).
CREATE TABLE loyalty.session_rotated_tokens (
    token_hash  CHAR(64)     PRIMARY KEY,                 -- SHA-256 en hexadecimal
    session_id  UUID         NOT NULL REFERENCES loyalty.sessions(id) ON DELETE CASCADE,
    rotated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_rotated_tokens_session ON loyalty.session_rotated_tokens (session_id);

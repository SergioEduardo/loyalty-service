-- =====================================================================
-- Activacion de cuentas por enlace de un solo uso.
-- =====================================================================

-- Una cuenta pendiente de activacion es una cuenta sin credencial: credential_hash NULL
-- (y sin google_subject) hasta que la persona elige su contrasena con el enlace. No hay
-- columna de estado: "pendiente" no puede desincronizarse del hash, y sin hash no hay
-- contrasena que adivinar. Las cuentas existentes tienen hash y no cambian.
ALTER TABLE loyalty.users DROP CONSTRAINT users_has_credential_check;
ALTER TABLE loyalty.users
    ADD CONSTRAINT users_identifiable_check CHECK (username IS NOT NULL OR google_subject IS NOT NULL);

-- Enlaces de activacion (tambien sirven para cambiar la contrasena de una cuenta activa).
-- Solo se guarda el SHA-256 del token. used_at se llena al usarlo o al emitir uno nuevo
-- para la misma cuenta: en ambos casos deja de servir.
CREATE TABLE loyalty.activation_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    token_hash  CHAR(64)     NOT NULL UNIQUE,              -- SHA-256 en hexadecimal
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, user_id) REFERENCES loyalty.users (tenant_id, id)
);
CREATE INDEX idx_activation_tokens_user ON loyalty.activation_tokens (tenant_id, user_id) WHERE used_at IS NULL;

-- Intentos fallidos de activacion, igual que login_attempts pero por clave: el slug
-- (exista o no, para no revelarlo) y el par slug + IP. Sin clave foranea a tenants.
CREATE TABLE loyalty.activation_attempts (
    attempt_key        VARCHAR(160) PRIMARY KEY,
    failures           INT          NOT NULL CHECK (failures > 0),
    window_started_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_activation_attempts_window ON loyalty.activation_attempts (window_started_at);

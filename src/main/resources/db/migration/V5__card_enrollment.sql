-- =====================================================================
-- Alta de tarjetas: verificacion del telefono y limites de solicitudes.
-- =====================================================================

-- Contadores de los limites del alta por clave, con ventana fija de una hora, como
-- activation_attempts: "verify-ip:{sha256 de la IP}" y "verify-tenant:{tenantId}" al pedir un
-- codigo, y "enroll-ip:{sha256 de la IP}" al pedir la tarjeta. La IP nunca se guarda en claro.
-- El limite por telefono no usa esta tabla: se cuenta con phone_verifications.created_at.
-- Sin clave foranea a tenants. Las filas vencidas se limpian periodicamente.
CREATE TABLE loyalty.enroll_rate_limits (
    limit_key          VARCHAR(100) PRIMARY KEY,
    hits               INT          NOT NULL CHECK (hits > 0),
    window_started_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_enroll_rate_limits_window ON loyalty.enroll_rate_limits (window_started_at);

-- El codigo se guarda como HMAC-SHA256 en hexadecimal (llave OTP_HASH_SALT, de Secret Manager)
-- de "{tenantId}|{phoneE164}|{codigo}".
COMMENT ON COLUMN loyalty.phone_verifications.code_hash IS
    'HMAC-SHA256 (hex) con OTP_HASH_SALT de "{tenantId}|{phoneE164}|{codigo}"';

-- Para limpiar verificaciones vencidas periodicamente.
CREATE INDEX idx_phone_verifications_expires ON loyalty.phone_verifications (expires_at);

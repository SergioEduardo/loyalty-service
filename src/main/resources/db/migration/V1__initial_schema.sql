-- =====================================================================
-- Programa de lealtad con Wallet
-- Modelo de datos. Un esquema por servicio en la misma base (Neon).
--
-- Reglas generales:
--   * Una sola unidad: puntos. Cada escaneo suma 1.
--   * Todas las tablas del loyalty-service llevan tenant_id y las claves
--     foraneas son compuestas (tenant_id, id), para que ningun registro
--     pueda apuntar a otro negocio. El filtro por negocio se aplica en la
--     aplicacion, con el negocio que viene en el token de la cuenta.
--   * El historial de movimientos es inmutable.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS loyalty;
CREATE SCHEMA IF NOT EXISTS wallet;

-- =====================================================================
-- loyalty-service
-- =====================================================================

-- Negocios y su configuracion
CREATE TABLE loyalty.tenants (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                 VARCHAR(50)  NOT NULL UNIQUE,
    business_name        VARCHAR(100) NOT NULL,
    description          VARCHAR(300),                                          -- bienvenida y "acerca de"
    logo_url             TEXT,                                                  -- logo normalizado en Cloud Storage
    brand_color          VARCHAR(7)   CHECK (brand_color ~ '^#[0-9A-Fa-f]{6}$'), -- un color; el texto se calcula por contraste
    points_goal          INT          NOT NULL CHECK (points_goal > 0),
    reward_text          VARCHAR(100) NOT NULL,
    cooldown_seconds     INT          NOT NULL DEFAULT 0 CHECK (cooldown_seconds >= 0),    -- 0 = apagada
    undo_window_seconds  INT          NOT NULL DEFAULT 300 CHECK (undo_window_seconds >= 0), -- valor por definir
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Cuentas del negocio: cajeros y duenos. Entran con usuario y contrasena
-- o con Google. El correo de una cuenta con usuario no se verifica ni se usa.
CREATE TABLE loyalty.users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES loyalty.tenants(id),
    name             VARCHAR(80)  NOT NULL,
    username         VARCHAR(60),                       -- nombre o correo que elige el negocio
    credential_hash  TEXT,                              -- hash de la contrasena (Argon2id o bcrypt)
    email            VARCHAR(254),                      -- correo verificado por Google
    google_subject   VARCHAR(255),                      -- identificador de la cuenta de Google
    role             VARCHAR(10)  NOT NULL DEFAULT 'CASHIER'
                     CHECK (role IN ('CASHIER', 'OWNER')),  -- OWNER incluye todo lo del cajero
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, username),
    UNIQUE (tenant_id, id),                             -- permite claves foraneas compuestas
    CONSTRAINT users_tenant_id_google_subject_key UNIQUE (tenant_id, google_subject),
    CONSTRAINT users_has_credential_check CHECK (credential_hash IS NOT NULL OR google_subject IS NOT NULL),
    CONSTRAINT users_username_required_check CHECK (credential_hash IS NULL OR username IS NOT NULL)
);

-- Sesiones. El token de renovacion se guarda solo con su hash.
CREATE TABLE loyalty.sessions (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                    UUID        NOT NULL,
    user_id                      UUID        NOT NULL,
    refresh_token_hash           CHAR(64)    NOT NULL UNIQUE,   -- SHA-256 en hexadecimal
    previous_refresh_token_hash  CHAR(64),                      -- detecta el reuso de un token rotado
    expires_at                   TIMESTAMPTZ NOT NULL,
    revoked_at                   TIMESTAMPTZ,
    last_used_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, user_id) REFERENCES loyalty.users (tenant_id, id)
);
CREATE INDEX idx_sessions_user ON loyalty.sessions (user_id) WHERE revoked_at IS NULL;

-- Verificacion del telefono por WhatsApp. El codigo se guarda solo con su hash.
-- Las filas vencidas se limpian periodicamente.
CREATE TABLE loyalty.phone_verifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES loyalty.tenants(id),
    phone_e164   VARCHAR(20)  NOT NULL CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    code_hash    CHAR(64)     NOT NULL,                  -- SHA-256 del codigo con una sal del servidor
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_phone_verifications_lookup
    ON loyalty.phone_verifications (tenant_id, phone_e164, created_at DESC);

-- Tarjetas: una por telefono en cada negocio. No hay tabla global de clientes.
-- El id (UUID v4) es tambien el serial del pase y lo que lleva el QR.
CREATE TABLE loyalty.cards (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES loyalty.tenants(id),
    phone_e164   VARCHAR(20)  NOT NULL CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    holder_name  VARCHAR(80),
    points       INT          NOT NULL DEFAULT 0 CHECK (points >= 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, phone_e164),
    UNIQUE (tenant_id, id)
);

-- Movimientos: historial inmutable. Deshacer es un movimiento nuevo de tipo UNDO.
CREATE TABLE loyalty.movements (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq                   BIGINT GENERATED ALWAYS AS IDENTITY,   -- orden dentro de la tarjeta
    tenant_id             UUID        NOT NULL,
    card_id               UUID        NOT NULL,
    user_id               UUID        NOT NULL,                  -- quien lo hizo: cajero o dueno
    type                  VARCHAR(20) NOT NULL
                          CHECK (type IN ('ADD_POINT', 'REDEEM_REWARD', 'UNDO')),
    points_delta          INT         NOT NULL,
    balance_after         INT         NOT NULL CHECK (balance_after >= 0),
    idempotency_key       UUID        NOT NULL,                  -- lo genera la PWA y lo reutiliza al reintentar
    reverses_movement_id  UUID,                                  -- solo en UNDO
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, idempotency_key),
    UNIQUE (reverses_movement_id),                               -- un movimiento se corrige una sola vez
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, card_id)              REFERENCES loyalty.cards (tenant_id, id),
    FOREIGN KEY (tenant_id, user_id)              REFERENCES loyalty.users (tenant_id, id),
    FOREIGN KEY (tenant_id, reverses_movement_id) REFERENCES loyalty.movements (tenant_id, id),
    CHECK ((type = 'UNDO') = (reverses_movement_id IS NOT NULL)),
    CHECK (
        (type = 'ADD_POINT'     AND points_delta = 1) OR
        (type = 'REDEEM_REWARD' AND points_delta < 0) OR
        (type = 'UNDO'          AND points_delta <> 0)
    )
);
CREATE INDEX idx_movements_card_seq ON loyalty.movements (card_id, seq DESC);

-- Inmutabilidad del historial
CREATE OR REPLACE FUNCTION loyalty.forbid_movement_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'movements es inmutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movements_immutable
    BEFORE UPDATE OR DELETE ON loyalty.movements
    FOR EACH ROW EXECUTE FUNCTION loyalty.forbid_movement_mutation();

-- Avisos para el wallet-service. Se escriben en la misma transaccion que el
-- cambio y se publican en Pub/Sub. El id creciente es la secuencia que usa el
-- wallet-service para ignorar avisos atrasados.
CREATE TABLE loyalty.outbox (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES loyalty.tenants(id),
    card_id          UUID,                                      -- null en avisos de configuracion
    event_type       VARCHAR(30)  NOT NULL
                     CHECK (event_type IN ('CardChanged', 'TenantChanged')),
    payload          JSONB        NOT NULL,                     -- estado completo, no solo el cambio
    status           VARCHAR(12)  NOT NULL DEFAULT 'PENDING'
                     CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON loyalty.outbox (next_attempt_at) WHERE status = 'PENDING';

-- Verificacion: los puntos de cada tarjeta deben ser la suma de sus movimientos
CREATE VIEW loyalty.v_card_balance_drift AS
SELECT c.id AS card_id,
       c.tenant_id,
       c.points,
       COALESCE(SUM(m.points_delta), 0) AS movements_sum
FROM loyalty.cards c
LEFT JOIN loyalty.movements m ON m.card_id = c.id
GROUP BY c.id
HAVING c.points <> COALESCE(SUM(m.points_delta), 0);

-- =====================================================================
-- wallet-service
-- =====================================================================

-- Copia de la configuracion del negocio que necesita para construir los pases
CREATE TABLE wallet.tenant_templates (
    tenant_id           UUID PRIMARY KEY,
    business_name       VARCHAR(100) NOT NULL,
    description         VARCHAR(300),
    logo_url            TEXT,
    brand_color         VARCHAR(7),
    pass_images         JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- URLs de las variantes de imagen para Apple y Google
    points_goal         INT          NOT NULL CHECK (points_goal > 0),
    reward_text         VARCHAR(100) NOT NULL,
    apple_pass_type_id  VARCHAR(100),               -- uno por negocio o compartido (por definir)
    google_class_id     VARCHAR(100),
    last_seq            BIGINT       NOT NULL DEFAULT 0,  -- ultimo aviso de configuracion aplicado
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Pases: estado actual de cada tarjeta en el wallet
CREATE TABLE wallet.passes (
    card_id           UUID PRIMARY KEY,              -- igual a loyalty.cards.id y al serial del pase
    tenant_id         UUID         NOT NULL REFERENCES wallet.tenant_templates(tenant_id),
    holder_name       VARCHAR(80),
    points            INT          NOT NULL DEFAULT 0 CHECK (points >= 0),
    last_seq          BIGINT       NOT NULL DEFAULT 0,  -- ultimo aviso aplicado; los anteriores se ignoran
    auth_token        VARCHAR(64)  NOT NULL,            -- authenticationToken de Apple, uno por pase
    google_object_id  VARCHAR(100),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()  -- base del tag "pases modificados desde" de Apple
);
CREATE INDEX idx_passes_tenant ON wallet.passes (tenant_id);

-- Dispositivos Apple registrados. Google no registra dispositivos.
CREATE TABLE wallet.device_registrations (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id                    UUID         NOT NULL REFERENCES wallet.passes(card_id) ON DELETE CASCADE,
    device_library_identifier  VARCHAR(100) NOT NULL,
    push_token                 VARCHAR(255) NOT NULL,
    is_valid                   BOOLEAN      NOT NULL DEFAULT TRUE,  -- false si APNs rechaza el token
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (card_id, device_library_identifier)
);
CREATE INDEX idx_registrations_device
    ON wallet.device_registrations (device_library_identifier) WHERE is_valid;

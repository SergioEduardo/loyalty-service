-- =====================================================================
-- Canal de envio del codigo de verificacion (WhatsApp o SMS).
-- =====================================================================

-- Solo para auditoria: los limites por telefono, IP y negocio, el vencimiento y los intentos no
-- dependen del canal, asi que cambiar de canal no reinicia ni duplica nada. NULL en las filas
-- anteriores a esta migracion.
ALTER TABLE loyalty.phone_verifications
    ADD COLUMN channel VARCHAR(16) CHECK (channel IN ('whatsapp', 'sms'));

COMMENT ON COLUMN loyalty.phone_verifications.channel IS
    'Canal por el que se envio el codigo (whatsapp | sms); NULL en filas anteriores a V6';

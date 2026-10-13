-- =====================================================================
-- Logo del negocio en Cloud Storage.
-- =====================================================================

-- Nombre del objeto del logo vigente (tenants/{id}/logo-{sha256}.png), para
-- borrarlo al reemplazarlo o quitarlo. logo_url guarda su URL publica, que es
-- la que leen la PWA y el wallet-service; las dos se llenan y vacian juntas.
ALTER TABLE loyalty.tenants ADD COLUMN logo_object_key TEXT;

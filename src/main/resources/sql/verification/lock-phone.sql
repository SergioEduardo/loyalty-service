-- Serializa las solicitudes de un mismo (negocio, telefono) hasta el fin de la transaccion,
-- para que el conteo de la ultima hora y el alta no dejen pasar un codigo de mas.
SELECT 1 AS locked
  FROM pg_advisory_xact_lock(hashtextextended(:lockKey, 0))

INSERT INTO loyalty.outbox (tenant_id, event_type, payload)
VALUES (:tenantId, :eventType, CAST(:payload AS jsonb))

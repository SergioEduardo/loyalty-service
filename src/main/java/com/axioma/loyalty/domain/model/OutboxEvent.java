package com.axioma.loyalty.domain.model;

import java.util.UUID;

/**
 * Aviso del outbox ya reclamado para publicar. id es su secuencia (la usa el wallet-service para
 * ignorar avisos atrasados); payload es el JSON del aviso. toString omite el payload: nunca se registra.
 */
public record OutboxEvent(long id, UUID tenantId, UUID cardId, String eventType, String payload, int attempts) {

    @Override
    public String toString() {
        return "OutboxEvent[id=" + id + ", tenantId=" + tenantId + ", cardId=" + cardId
                + ", eventType=" + eventType + ", attempts=" + attempts + "]";
    }
}

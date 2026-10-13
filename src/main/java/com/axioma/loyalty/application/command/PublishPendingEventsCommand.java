package com.axioma.loyalty.application.command;

import java.util.List;

/**
 * Publicar avisos pendientes: un lote de los vencidos (red de seguridad) o solo los indicados
 * (camino rápido, después del commit de la operación que los creó).
 */
public record PublishPendingEventsCommand(int batchSize, List<Long> eventIds) {

    public static PublishPendingEventsCommand due(int batchSize) {
        return new PublishPendingEventsCommand(batchSize, null);
    }

    public static PublishPendingEventsCommand only(List<Long> eventIds) {
        return new PublishPendingEventsCommand(eventIds == null ? 0 : eventIds.size(), eventIds);
    }

    public boolean targeted() {
        return eventIds != null;
    }
}

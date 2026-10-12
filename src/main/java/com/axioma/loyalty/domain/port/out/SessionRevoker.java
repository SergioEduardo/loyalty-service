package com.axioma.loyalty.domain.port.out;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SessionRevoker {

    /** Revoca las sesiones vigentes de la cuenta y devuelve cuántas revocó. */
    Mono<Long> revokeAllByUser(UUID tenantId, UUID userId);
}

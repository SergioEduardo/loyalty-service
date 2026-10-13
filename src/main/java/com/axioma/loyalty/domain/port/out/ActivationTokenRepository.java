package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.ActivationTarget;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ActivationTokenRepository {

    Mono<Void> create(UUID tenantId, UUID userId, String tokenHash, Instant expiresAt);

    /** Los enlaces sin usar de la cuenta dejan de servir. */
    Mono<Void> invalidateAll(UUID tenantId, UUID userId);

    /** Vacío si el enlace no existe, venció, ya se usó, es de otro negocio o la cuenta o el negocio están inactivos. */
    Mono<ActivationTarget> findValid(String slug, String tokenHash);

    /** Como findValid, pero además lo marca usado en la misma sentencia. */
    Mono<ActivationTarget> consume(String slug, String tokenHash);
}

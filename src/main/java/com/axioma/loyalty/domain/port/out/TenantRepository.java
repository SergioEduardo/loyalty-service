package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface TenantRepository {

    /** Falla con SLUG_ALREADY_EXISTS si la dirección ya está en uso. */
    Mono<Tenant> create(Tenant tenant);

    /** Vacío si no existe. */
    Mono<Tenant> findById(UUID tenantId);

    /** Vacío si no existe. */
    Mono<Tenant> findBySlug(Slug slug);

    /** Lee y bloquea la fila hasta el fin de la transacción. Vacío si no existe. */
    Mono<Tenant> findByIdForUpdate(UUID tenantId);

    Mono<Tenant> update(Tenant tenant);
}

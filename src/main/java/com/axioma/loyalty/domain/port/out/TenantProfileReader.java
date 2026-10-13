package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.TenantProfile;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface TenantProfileReader {

    /** Vacío si el negocio no existe o está desactivado. */
    Mono<TenantProfile> findActiveById(UUID tenantId);
}

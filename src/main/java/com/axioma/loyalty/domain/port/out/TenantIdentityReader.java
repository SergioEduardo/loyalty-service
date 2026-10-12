package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.TenantIdentity;
import reactor.core.publisher.Mono;

public interface TenantIdentityReader {

    /** Vacío si el negocio no existe o está desactivado. */
    Mono<TenantIdentity> findActiveBySlug(String slug);
}

package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.TenantChanged;
import reactor.core.publisher.Mono;

/** Avisos pendientes de publicar. Debe llamarse dentro de la transacción del cambio. */
public interface TenantEventOutbox {

    /** Devuelve el id del aviso (su secuencia). */
    Mono<Long> append(TenantChanged event);
}

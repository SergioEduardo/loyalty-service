package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.AccessTokenClaims;
import reactor.core.publisher.Mono;

/** Verifica firma, issuer y vigencia. No consulta la sesión: eso lo hace el caso de uso. */
public interface AccessTokenDecoder {

    /** Falla con UNAUTHORIZED si el token no es válido o AUTH_UNAVAILABLE si no hay clave. */
    Mono<AccessTokenClaims> decode(String token);
}

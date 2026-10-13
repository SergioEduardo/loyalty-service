package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.IssuedAccessToken;
import com.axioma.loyalty.domain.model.SessionOwner;
import reactor.core.publisher.Mono;

/** Emite access tokens firmados. */
public interface TokenIssuer {

    /** Falla con AUTH_UNAVAILABLE si no hay una clave de firma utilizable. */
    Mono<Void> ensureReady();

    Mono<IssuedAccessToken> issue(SessionOwner owner);
}

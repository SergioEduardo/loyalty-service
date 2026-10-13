package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import reactor.core.publisher.Mono;

/** Arma la respuesta de iniciar o renovar sesión: access token nuevo más el refresh token. */
final class SessionTokens {

    private SessionTokens() {
    }

    static Mono<AuthSession> issue(TokenIssuer tokenIssuer, SessionOwner owner, String refreshToken) {
        return tokenIssuer.issue(owner)
                .map(access -> new AuthSession(access.value(), refreshToken, access.expiresInSeconds(), owner));
    }
}

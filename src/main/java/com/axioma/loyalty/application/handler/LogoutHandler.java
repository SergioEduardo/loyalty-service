package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.LogoutCommand;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Revoca la sesión del refresh token. Idempotente: un token desconocido no es error. */
@Service
@RequiredArgsConstructor
public class LogoutHandler implements Handler<LogoutCommand, Mono<Void>> {

    private final SessionRepository sessionRepository;
    private final RefreshTokenGenerator refreshTokens;

    @Override
    public Mono<Void> handle(LogoutCommand command) {
        String token = command.refreshToken();
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return sessionRepository.revokeByRefreshTokenHash(refreshTokens.hash(token)).then();
    }
}

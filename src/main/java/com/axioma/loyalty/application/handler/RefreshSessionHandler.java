package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.RefreshSessionCommand;
import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Renueva la sesión rotando el refresh token. Si el token no es el vigente de una sesión válida
 * (ya rotado, revocado o vencido) se revoca la sesión a la que pertenezca: un token rotado que
 * vuelve a llegar indica que alguien más lo tiene.
 * Sin @Transactional: la rotación es una sola sentencia y la revocación debe quedar aunque se
 * responda con error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionHandler implements Handler<RefreshSessionCommand, Mono<AuthSession>> {

    private final TokenIssuer tokenIssuer;
    private final SessionRepository sessionRepository;
    private final RefreshTokenGenerator refreshTokens;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    public Mono<AuthSession> handle(RefreshSessionCommand command) {
        String token = command.refreshToken();
        if (token == null || token.isBlank()) {
            return Mono.error(new LoyaltyException(ErrorCode.INVALID_SESSION));
        }
        // Primero la clave: no rotar un token si después no se puede emitir el access token.
        return tokenIssuer.ensureReady().then(Mono.defer(() -> rotate(token)));
    }

    private Mono<AuthSession> rotate(String token) {
        String currentHash = refreshTokens.hash(token);
        String newToken = refreshTokens.generate();
        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(properties.refreshTokenTtlDays()));
        return sessionRepository.rotate(currentHash, refreshTokens.hash(newToken), expiresAt)
                .flatMap(owner -> SessionTokens.issue(tokenIssuer, owner, newToken))
                .switchIfEmpty(Mono.defer(() -> rejectAndRevoke(currentHash)));
    }

    private Mono<AuthSession> rejectAndRevoke(String hash) {
        return sessionRepository.revokeByRefreshTokenHash(hash)
                .doOnNext(revoked -> {
                    if (revoked > 0) {
                        log.warn("Refresh token reuse detected; session revoked");
                    }
                })
                .then(Mono.error(new LoyaltyException(ErrorCode.INVALID_SESSION)));
    }
}

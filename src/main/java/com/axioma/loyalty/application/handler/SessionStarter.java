package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.model.NewSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.port.out.LoginAttemptStore;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Abre una sesión nueva para una cuenta ya verificada (login o activación): limpia sus fallos de
 * inicio de sesión, guarda el hash del refresh token y emite el access token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStarter {

    private final LoginAttemptStore loginAttempts;
    private final SessionRepository sessionRepository;
    private final RefreshTokenGenerator refreshTokens;
    private final TokenIssuer tokenIssuer;
    private final AuthProperties properties;
    private final Clock clock;

    public Mono<AuthSession> start(UserAccount user, String tenantSlug, String tenantName) {
        String refreshToken = refreshTokens.generate();
        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(properties.refreshTokenTtlDays()));
        var session = new NewSession(UUID.randomUUID(), user.tenantId(), user.id(),
                refreshTokens.hash(refreshToken), expiresAt);
        var owner = new SessionOwner(session.id(), user.tenantId(), user.id(), user.name(), user.role(),
                tenantSlug, tenantName);
        return loginAttempts.clear(user.tenantId(), user.username())
                .then(sessionRepository.create(session))
                .then(Mono.defer(() -> SessionTokens.issue(tokenIssuer, owner, refreshToken)))
                .doOnNext(auth -> log.info("Session started tenantId={} userId={} sessionId={}",
                        user.tenantId(), user.id(), session.id()));
    }
}

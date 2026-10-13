package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.LoginCommand;
import com.axioma.loyalty.application.validator.LoginValidator;
import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.model.NewSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.TooManyAttemptsException;
import com.axioma.loyalty.domain.port.out.LoginAttemptStore;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Inicio de sesión del personal por negocio + usuario + contraseña.
 * Negocio inexistente o inactivo, cuenta inexistente o inactiva y contraseña incorrecta dan el
 * mismo INVALID_CREDENTIALS, y siempre se ejecuta un bcrypt para que tarden lo mismo.
 * Sin transacción: cada escritura es atómica por sí sola y el bcrypt no retiene una conexión.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHandler implements Handler<LoginCommand, Mono<AuthSession>> {

    private final LoginValidator validator;
    private final TokenIssuer tokenIssuer;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptStore loginAttempts;
    private final SessionRepository sessionRepository;
    private final RefreshTokenGenerator refreshTokens;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    public Mono<AuthSession> handle(LoginCommand command) {
        return tokenIssuer.ensureReady()
                .then(validator.validate(command))
                .then(Mono.defer(() -> findActiveTenant(command.slug())))
                .flatMap(tenant -> loginToTenant(tenant, command))
                .switchIfEmpty(Mono.defer(() -> rejectWithoutAccount(command.password())));
    }

    private Mono<Tenant> findActiveTenant(String slug) {
        if (!Slug.isValid(slug)) {
            return Mono.empty();
        }
        return tenantRepository.findBySlug(new Slug(slug)).filter(Tenant::active);
    }

    private Mono<AuthSession> loginToTenant(Tenant tenant, LoginCommand command) {
        int window = properties.loginWindowMinutes();
        return loginAttempts.find(tenant.id(), command.username(), window)
                .filter(throttle -> throttle.failures() >= properties.loginMaxAttempts())
                .flatMap(throttle -> Mono.<AuthSession>error(
                        new TooManyAttemptsException(throttle.retryAfterSeconds())))
                .switchIfEmpty(Mono.defer(() -> checkPassword(tenant, command)));
    }

    /**
     * Exactamente un bcrypt por intento: contra el hash de la cuenta o, si no hay cuenta activa
     * con contraseña, contra el ficticio.
     */
    private Mono<AuthSession> checkPassword(Tenant tenant, LoginCommand command) {
        return userRepository.findByUsername(tenant.id(), command.username())
                .filter(UserAccount::active)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> passwordHasher
                        .matches(command.password(), user.map(UserAccount::credentialHash).orElse(null))
                        .flatMap(ok -> ok && user.isPresent()
                                ? startSession(tenant, user.get())
                                : recordFailure(tenant, command.username())));
    }

    private Mono<AuthSession> recordFailure(Tenant tenant, String username) {
        return loginAttempts.recordFailure(tenant.id(), username, properties.loginWindowMinutes())
                .flatMap(throttle -> {
                    log.info("Login failed tenantId={} failures={}", tenant.id(), throttle.failures());
                    return Mono.error(throttle.failures() >= properties.loginMaxAttempts()
                            ? new TooManyAttemptsException(throttle.retryAfterSeconds())
                            : new LoyaltyException(ErrorCode.INVALID_CREDENTIALS));
                });
    }

    private Mono<AuthSession> rejectWithoutAccount(String password) {
        return passwordHasher.matches(password, null)
                .then(Mono.error(new LoyaltyException(ErrorCode.INVALID_CREDENTIALS)));
    }

    private Mono<AuthSession> startSession(Tenant tenant, UserAccount user) {
        String refreshToken = refreshTokens.generate();
        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(properties.refreshTokenTtlDays()));
        var session = new NewSession(UUID.randomUUID(), tenant.id(), user.id(),
                refreshTokens.hash(refreshToken), expiresAt);
        var owner = new SessionOwner(session.id(), tenant.id(), user.id(), user.name(), user.role(),
                tenant.slug().value(), tenant.name());
        return loginAttempts.clear(tenant.id(), user.username())
                .then(sessionRepository.create(session))
                .then(Mono.defer(() -> SessionTokens.issue(tokenIssuer, owner, refreshToken)))
                .doOnNext(auth -> log.info("Login ok tenantId={} userId={} sessionId={}",
                        tenant.id(), user.id(), session.id()));
    }
}

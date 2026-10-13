package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.ActivationProperties;
import com.axioma.loyalty.domain.model.ActivationLink;
import com.axioma.loyalty.domain.model.CreatedUser;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.port.out.ActivationTokenGenerator;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Emite enlaces de activación de un solo uso. En la base solo queda el hash del token; el token
 * nunca se registra. URL: {APP_WEB_BASE_URL}/caja/{slug}/activar#t={token} (el fragmento no
 * llega a ningún servidor).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivationLinkIssuer {

    private final ActivationTokenGenerator tokenGenerator;
    private final ActivationTokenRepository activationTokens;
    private final UserRepository userRepository;
    private final ActivationProperties properties;
    private final Clock clock;

    /** Crea la cuenta sin contraseña y su primer enlace en la misma transacción. */
    @Transactional
    public Mono<CreatedUser> createPendingUser(Tenant tenant, UserAccount user) {
        return userRepository.create(user)
                .flatMap(saved -> issue(tenant, saved).map(link -> new CreatedUser(saved, link)));
    }

    /** Enlace nuevo para una cuenta existente; los anteriores dejan de servir. */
    @Transactional
    public Mono<ActivationLink> reissue(Tenant tenant, UserAccount user) {
        return activationTokens.invalidateAll(user.tenantId(), user.id())
                .then(Mono.defer(() -> issue(tenant, user)));
    }

    private Mono<ActivationLink> issue(Tenant tenant, UserAccount user) {
        String token = tokenGenerator.generate();
        Instant expiresAt = Instant.now(clock).plus(Duration.ofHours(properties.tokenTtlHours()))
                .truncatedTo(ChronoUnit.SECONDS);
        return activationTokens.create(user.tenantId(), user.id(), tokenGenerator.hash(token), expiresAt)
                .then(Mono.fromSupplier(() -> new ActivationLink(token, expiresAt, url(tenant, token))))
                .doOnNext(link -> log.info("Activation link issued tenantId={} userId={} expiresAt={}",
                        user.tenantId(), user.id(), expiresAt));
    }

    private String url(Tenant tenant, String token) {
        if (properties.webBaseUrl() == null) {
            return null;
        }
        return properties.webBaseUrl() + "/caja/" + tenant.slug().value() + "/activar#t=" + token;
    }
}

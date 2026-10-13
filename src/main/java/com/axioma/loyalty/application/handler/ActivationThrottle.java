package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.ActivationProperties;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.TooManyAttemptsException;
import com.axioma.loyalty.domain.port.out.ActivationAttemptStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Límite de intentos fallidos de activación, con el mismo mecanismo que el login: por slug + IP
 * (si se conoce) y por slug. Cuenta aunque el slug no exista, para no revelarlo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivationThrottle {

    private static final int SLUG_KEY_MAX = 60;

    private final ActivationAttemptStore attempts;
    private final ActivationProperties properties;

    public record Limit(String key, int maxFailures) {
    }

    public List<Limit> limits(String slug, String clientIp) {
        String slugKey = slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT);
        if (slugKey.length() > SLUG_KEY_MAX) {
            slugKey = slugKey.substring(0, SLUG_KEY_MAX);
        }
        List<Limit> limits = new ArrayList<>(2);
        if (clientIp != null) {
            limits.add(new Limit("slug-ip:" + slugKey + "|" + clientIp, properties.maxAttemptsPerIp()));
        }
        limits.add(new Limit("slug:" + slugKey, properties.maxAttemptsPerSlug()));
        return limits;
    }

    /** 429 si alguna clave ya llegó a su límite dentro de la ventana. */
    public Mono<Void> ensureAllowed(List<Limit> limits) {
        return Flux.fromIterable(limits)
                .concatMap(limit -> attempts.find(limit.key(), properties.windowMinutes())
                        .filter(throttle -> throttle.failures() >= limit.maxFailures()))
                .next()
                .flatMap(throttle -> Mono.error(new TooManyAttemptsException(throttle.retryAfterSeconds())));
    }

    /** Registra el fallo en todas las claves y responde INVALID_ACTIVATION, o 429 si con este se llegó al límite. */
    public <T> Mono<T> reject(List<Limit> limits) {
        return Flux.fromIterable(limits)
                .concatMap(limit -> attempts.recordFailure(limit.key(), properties.windowMinutes())
                        .filter(throttle -> throttle.failures() >= limit.maxFailures()))
                .map(LoginThrottle::retryAfterSeconds)
                .reduce(Math::max)
                .<T>flatMap(retryAfter -> {
                    log.warn("Activation attempts limit reached");
                    return Mono.error(new TooManyAttemptsException(retryAfter));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Activation rejected");
                    return Mono.error(new LoyaltyException(ErrorCode.INVALID_ACTIVATION));
                }));
    }

    public Mono<Void> clear(List<Limit> limits) {
        return Flux.fromIterable(limits).concatMap(limit -> attempts.clear(limit.key())).then();
    }
}

package com.axioma.loyalty.adapter.out.security;

import com.axioma.loyalty.domain.port.out.PasswordHasher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** bcrypt es CPU intensivo: corre en un scheduler propio para no bloquear el event loop. */
@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final Scheduler scheduler;
    /** Mismo costo que los hashes reales, para que comparar contra él tarde lo mismo. */
    private final String dummyHash;

    public BcryptPasswordHasher(BCryptPasswordEncoder encoder,
                                @Qualifier("passwordHashScheduler") Scheduler scheduler) {
        this.encoder = encoder;
        this.scheduler = scheduler;
        this.dummyHash = encoder.encode("cuenta-inexistente");
    }

    @Override
    public Mono<String> hash(String rawPassword) {
        return Mono.fromCallable(() -> encoder.encode(rawPassword)).subscribeOn(scheduler);
    }

    @Override
    public Mono<Boolean> matches(String rawPassword, String hashOrNull) {
        return Mono.fromCallable(() -> {
            if (hashOrNull == null) {
                encoder.matches(rawPassword, dummyHash);
                return false;
            }
            return encoder.matches(rawPassword, hashOrNull);
        }).subscribeOn(scheduler);
    }
}

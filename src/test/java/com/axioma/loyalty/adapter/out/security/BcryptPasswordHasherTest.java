package com.axioma.loyalty.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class BcryptPasswordHasherTest {

    @Test
    void hashesOffTheCallerThread() {
        var encoder = new BCryptPasswordEncoder(4);
        var scheduler = Schedulers.newBoundedElastic(1, 10, "password-hash-test");
        var hasher = new BcryptPasswordHasher(encoder, scheduler);
        try {
            StepVerifier.create(hasher.hash("secreto123"))
                    .assertNext(hash -> {
                        assertThat(hash).startsWith("$2a$04$").doesNotContain("secreto123");
                        assertThat(encoder.matches("secreto123", hash)).isTrue();
                        assertThat(Thread.currentThread().getName()).startsWith("password-hash-test");
                    })
                    .verifyComplete();
        } finally {
            scheduler.dispose();
        }
    }
}

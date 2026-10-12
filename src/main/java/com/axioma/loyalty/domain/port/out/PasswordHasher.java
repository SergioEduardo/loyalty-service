package com.axioma.loyalty.domain.port.out;

import reactor.core.publisher.Mono;

public interface PasswordHasher {

    Mono<String> hash(String rawPassword);
}

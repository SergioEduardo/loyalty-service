package com.axioma.loyalty.domain.port.out;

import reactor.core.publisher.Mono;

public interface PasswordHasher {

    Mono<String> hash(String rawPassword);

    /**
     * Compara la contraseña con el hash. Si no hay hash (cuenta inexistente) compara contra uno
     * ficticio y devuelve false, para que la respuesta tarde lo mismo.
     */
    Mono<Boolean> matches(String rawPassword, String hashOrNull);
}

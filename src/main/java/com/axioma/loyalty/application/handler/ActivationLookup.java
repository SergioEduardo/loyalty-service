package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.ActivationTarget;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.port.out.ActivationTokenGenerator;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Busca un enlace válido por slug y token. Entradas imposibles (slug inválido, token vacío o enorme) dan vacío. */
@Component
@RequiredArgsConstructor
public class ActivationLookup {

    /** Un token emitido tiene 43 caracteres; se deja margen sin hashear entradas arbitrariamente grandes. */
    private static final int TOKEN_MAX = 128;

    private final ActivationTokenGenerator tokenGenerator;
    private final ActivationTokenRepository activationTokens;

    public boolean isWellFormed(String slug, String token) {
        return Slug.isValid(slug) && token != null && !token.isBlank() && token.length() <= TOKEN_MAX;
    }

    public String hash(String token) {
        return tokenGenerator.hash(token);
    }

    public Mono<ActivationTarget> findValid(String slug, String token) {
        if (!isWellFormed(slug, token)) {
            return Mono.empty();
        }
        return activationTokens.findValid(slug, tokenGenerator.hash(token));
    }
}

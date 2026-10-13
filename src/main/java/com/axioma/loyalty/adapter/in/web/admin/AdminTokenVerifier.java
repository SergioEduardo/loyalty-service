package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.adapter.in.web.GoogleIdTokenVerifier;
import com.axioma.loyalty.config.AdminProperties;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Valida el encabezado Authorization de la API admin: un ID token de Google con ADMIN_AUDIENCE y un
 * correo de ADMIN_ALLOWED_EMAILS (ver GoogleIdTokenVerifier). Devuelve el correo. Falla con
 * UNAUTHORIZED (token ausente o inválido) o FORBIDDEN.
 */
@Component
public class AdminTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public AdminTokenVerifier(ReactiveJwtDecoder decoder, AdminProperties properties) {
        this.verifier = new GoogleIdTokenVerifier(decoder, properties.audience(), properties.allowedEmails());
    }

    public Mono<String> verify(String authorizationHeader) {
        return verifier.verify(authorizationHeader);
    }
}

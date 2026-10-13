package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.config.SecurityConfig;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Valida un encabezado Authorization con un ID token de Google (firma, vigencia e issuer los revisa
 * el decoder) con la audiencia esperada y un correo verificado y permitido. Devuelve el correo.
 * Falla con UNAUTHORIZED (token ausente o inválido) o FORBIDDEN (correo no permitido, o sin
 * audiencia o correos configurados: cerrado). Lo usan la API admin y los endpoints internos.
 */
public final class GoogleIdTokenVerifier {

    private static final String BEARER_PREFIX = "bearer ";

    private final ReactiveJwtDecoder decoder;
    private final String audience;
    private final List<String> allowedEmails;

    public GoogleIdTokenVerifier(ReactiveJwtDecoder decoder, String audience, List<String> allowedEmails) {
        this.decoder = decoder;
        this.audience = audience == null ? "" : audience.strip();
        this.allowedEmails = allowedEmails == null ? List.of() : allowedEmails.stream()
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .toList();
    }

    public Mono<String> verify(String authorizationHeader) {
        if (audience.isEmpty() || allowedEmails.isEmpty()) {
            return Mono.error(new LoyaltyException(ErrorCode.FORBIDDEN));
        }
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return Mono.error(new LoyaltyException(ErrorCode.UNAUTHORIZED));
        }
        return Mono.defer(() -> decoder.decode(token))
                .onErrorMap(JwtException.class, e -> new LoyaltyException(ErrorCode.UNAUTHORIZED))
                .map(this::authorizedEmail);
    }

    private String authorizedEmail(Jwt jwt) {
        boolean issuerOk = jwt.getIssuer() != null
                && SecurityConfig.GOOGLE_ISSUER.equals(jwt.getIssuer().toString());
        boolean audienceOk = jwt.getAudience() != null && jwt.getAudience().contains(audience);
        if (!issuerOk || !audienceOk) {
            throw new LoyaltyException(ErrorCode.UNAUTHORIZED);
        }
        String email = jwt.getClaimAsString("email");
        boolean verified = Boolean.parseBoolean(jwt.getClaimAsString("email_verified"));
        if (email == null || !verified || !allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            throw new LoyaltyException(ErrorCode.FORBIDDEN);
        }
        return email;
    }

    private static String bearerToken(String header) {
        if (header == null || header.length() <= BEARER_PREFIX.length()
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}

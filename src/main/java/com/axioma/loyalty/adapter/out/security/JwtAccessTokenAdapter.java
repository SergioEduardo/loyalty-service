package com.axioma.loyalty.adapter.out.security;

import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AccessTokenClaims;
import com.axioma.loyalty.domain.model.IssuedAccessToken;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.AccessTokenDecoder;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Access tokens JWT HS256 firmados con JWT_SIGNING_KEY. Si la clave falta o mide menos de
 * 32 bytes, emitir y verificar fallan con AUTH_UNAVAILABLE (503). Nunca registra la clave.
 */
@Slf4j
@Component
public class JwtAccessTokenAdapter implements TokenIssuer, AccessTokenDecoder {

    public static final String ISSUER = "loyalty-service";

    private final byte[] key;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtAccessTokenAdapter(AuthProperties properties, Clock clock) {
        this.key = properties.hasUsableSigningKey()
                ? properties.jwtSigningKey().getBytes(StandardCharsets.UTF_8)
                : null;
        this.ttlSeconds = properties.accessTokenTtlSeconds();
        this.clock = clock;
        if (key == null) {
            log.warn("JWT_SIGNING_KEY ausente o menor a {} bytes: el inicio de sesión responderá 503",
                    AuthProperties.MIN_KEY_BYTES);
        }
    }

    @Override
    public Mono<Void> ensureReady() {
        return key == null ? Mono.error(new LoyaltyException(ErrorCode.AUTH_UNAVAILABLE)) : Mono.empty();
    }

    @Override
    public Mono<IssuedAccessToken> issue(SessionOwner owner) {
        return ensureReady().then(Mono.fromCallable(() -> sign(owner)));
    }

    private IssuedAccessToken sign(SessionOwner owner) throws JOSEException {
        Instant now = Instant.now(clock);
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(owner.userId().toString())
                .claim("tid", owner.tenantId().toString())
                .claim("role", owner.role().name())
                .claim("sid", owner.sessionId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(key));
        return new IssuedAccessToken(jwt.serialize(), ttlSeconds);
    }

    @Override
    public Mono<AccessTokenClaims> decode(String token) {
        return ensureReady().then(Mono.fromCallable(() -> verify(token)));
    }

    private AccessTokenClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))) {
                throw unauthorized();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer())
                    || expiration == null
                    || !Instant.now(clock).isBefore(expiration.toInstant())) {
                throw unauthorized();
            }
            return new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getStringClaim("tid")),
                    UserRole.valueOf(claims.getStringClaim("role")),
                    UUID.fromString(claims.getStringClaim("sid")));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            throw unauthorized();
        }
    }

    private static LoyaltyException unauthorized() {
        return new LoyaltyException(ErrorCode.UNAUTHORIZED);
    }
}

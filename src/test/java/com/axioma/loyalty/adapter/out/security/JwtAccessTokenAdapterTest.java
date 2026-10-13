package com.axioma.loyalty.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AccessTokenClaims;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class JwtAccessTokenAdapterTest {

    private static final String KEY = "clave-de-prueba-de-al-menos-32-bytes!!";
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final SessionOwner OWNER = new SessionOwner(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "Ana", UserRole.CASHIER, "mi-cafe", "Mi Café");

    private static JwtAccessTokenAdapter adapter(String key, Instant now) {
        return new JwtAccessTokenAdapter(new AuthProperties(key, 900, 30, 5, 15), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Consumer<Throwable> hasCode(ErrorCode code) {
        return error -> assertThat(((LoyaltyException) error).getCode()).isEqualTo(code);
    }

    private static String issue(JwtAccessTokenAdapter adapter) {
        return adapter.issue(OWNER).block().value();
    }

    @Test
    void issuesAndVerifiesToken() {
        var adapter = adapter(KEY, NOW);
        var issued = adapter.issue(OWNER).block();

        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        StepVerifier.create(adapter.decode(issued.value()))
                .expectNext(new AccessTokenClaims(OWNER.userId(), OWNER.tenantId(), UserRole.CASHIER, OWNER.sessionId()))
                .verifyComplete();
    }

    @Test
    void tokenCarriesExpectedClaims() throws Exception {
        var claims = SignedJWT.parse(issue(adapter(KEY, NOW))).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("loyalty-service");
        assertThat(claims.getSubject()).isEqualTo(OWNER.userId().toString());
        assertThat(claims.getStringClaim("tid")).isEqualTo(OWNER.tenantId().toString());
        assertThat(claims.getStringClaim("sid")).isEqualTo(OWNER.sessionId().toString());
        assertThat(claims.getStringClaim("role")).isEqualTo("CASHIER");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void rejectsExpiredToken() {
        String token = issue(adapter(KEY, NOW));

        StepVerifier.create(adapter(KEY, NOW.plusSeconds(900)).decode(token))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = issue(adapter("otra-clave-distinta-de-al-menos-32-bytes", NOW));

        StepVerifier.create(adapter(KEY, NOW).decode(token))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer("otro-servicio")
                .subject(OWNER.userId().toString())
                .claim("tid", OWNER.tenantId().toString())
                .claim("role", "OWNER")
                .claim("sid", OWNER.sessionId().toString())
                .expirationTime(Date.from(NOW.plusSeconds(60)))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(KEY.getBytes()));

        StepVerifier.create(adapter(KEY, NOW).decode(jwt.serialize()))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsGarbageAndUnsignedTokens() {
        String unsigned = "eyJhbGciOiJub25lIn0." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"loyalty-service\"}".getBytes()) + ".";

        StepVerifier.create(adapter(KEY, NOW).decode("no-es-un-jwt"))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        StepVerifier.create(adapter(KEY, NOW).decode(unsigned))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void unavailableWithoutUsableKey() {
        var shortKey = adapter("corta", NOW);
        var noKey = adapter(null, NOW);

        StepVerifier.create(shortKey.ensureReady()).expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
        StepVerifier.create(noKey.issue(OWNER)).expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
        StepVerifier.create(noKey.decode("x")).expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(new AuthProperties(KEY, 900, 30, 5, 15).toString()).doesNotContain(KEY);
    }
}

package com.axioma.loyalty.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.config.AdminProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminTokenVerifierTest {

    private static final String AUDIENCE = "https://loyalty.example.run.app";
    private static final String N8N = "n8n@project.iam.gserviceaccount.com";

    private final ReactiveJwtDecoder decoder = mock(ReactiveJwtDecoder.class);
    private final AdminTokenVerifier verifier =
            new AdminTokenVerifier(decoder, new AdminProperties(AUDIENCE, List.of(" N8N@project.iam.gserviceaccount.com ")));

    private static Jwt token(String issuer, String audience, String email, boolean verified) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(List.of(audience))
                .claim("email", email)
                .claim("email_verified", verified)
                .build();
    }

    private static Consumer<Throwable> hasCode(ErrorCode code) {
        return error -> assertThat(((LoyaltyException) error).getCode()).isEqualTo(code);
    }

    @Test
    void acceptsAllowedServiceAccount() {
        when(decoder.decode("good")).thenReturn(Mono.just(token("https://accounts.google.com", AUDIENCE, N8N, true)));

        StepVerifier.create(verifier.verify("Bearer good")).expectNext(N8N).verifyComplete();
    }

    @Test
    void rejectsMissingOrNonBearerHeaderWith401() {
        StepVerifier.create(verifier.verify(null)).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        StepVerifier.create(verifier.verify("Basic abc")).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        StepVerifier.create(verifier.verify("Bearer  ")).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        verifyNoInteractions(decoder);
    }

    @Test
    void rejectsInvalidSignatureWith401() {
        when(decoder.decode("bad")).thenReturn(Mono.error(new BadJwtException("firma inválida")));

        StepVerifier.create(verifier.verify("Bearer bad")).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsWrongAudienceOrIssuerWith401() {
        when(decoder.decode("aud")).thenReturn(Mono.just(token("https://accounts.google.com", "https://otro", N8N, true)));
        when(decoder.decode("iss")).thenReturn(Mono.just(token("https://evil.example", AUDIENCE, N8N, true)));

        StepVerifier.create(verifier.verify("Bearer aud")).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        StepVerifier.create(verifier.verify("Bearer iss")).expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsUnlistedOrUnverifiedEmailWith403() {
        when(decoder.decode("other")).thenReturn(Mono.just(
                token("https://accounts.google.com", AUDIENCE, "otro@gmail.com", true)));
        when(decoder.decode("unverified")).thenReturn(Mono.just(
                token("https://accounts.google.com", AUDIENCE, N8N, false)));

        StepVerifier.create(verifier.verify("Bearer other")).expectErrorSatisfies(hasCode(ErrorCode.FORBIDDEN)).verify();
        StepVerifier.create(verifier.verify("Bearer unverified")).expectErrorSatisfies(hasCode(ErrorCode.FORBIDDEN)).verify();
    }

    @Test
    void closedWhenNotConfigured() {
        var closed = new AdminTokenVerifier(decoder, new AdminProperties("", List.of()));
        var noEmails = new AdminTokenVerifier(decoder, new AdminProperties(AUDIENCE, List.of("")));

        StepVerifier.create(closed.verify("Bearer good")).expectErrorSatisfies(hasCode(ErrorCode.FORBIDDEN)).verify();
        StepVerifier.create(noEmails.verify("Bearer good")).expectErrorSatisfies(hasCode(ErrorCode.FORBIDDEN)).verify();
        verifyNoInteractions(decoder);
    }
}

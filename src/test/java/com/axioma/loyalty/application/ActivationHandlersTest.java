package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.TENANT_ID;
import static com.axioma.loyalty.application.AdminFixtures.USER_ID;
import static com.axioma.loyalty.application.AdminFixtures.hasCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.command.ActivateAccountCommand;
import com.axioma.loyalty.application.handler.AccountActivationWriter;
import com.axioma.loyalty.application.handler.ActivateAccountHandler;
import com.axioma.loyalty.application.handler.ActivationLookup;
import com.axioma.loyalty.application.handler.ActivationThrottle;
import com.axioma.loyalty.application.handler.InspectActivationHandler;
import com.axioma.loyalty.application.handler.SessionStarter;
import com.axioma.loyalty.application.query.InspectActivationQuery;
import com.axioma.loyalty.application.validator.ActivateAccountValidator;
import com.axioma.loyalty.application.validator.PasswordPolicy;
import com.axioma.loyalty.config.ActivationProperties;
import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.config.SecurityProperties;
import com.axioma.loyalty.domain.model.ActivationTarget;
import com.axioma.loyalty.domain.model.IssuedAccessToken;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.TooManyAttemptsException;
import com.axioma.loyalty.domain.port.out.ActivationAttemptStore;
import com.axioma.loyalty.domain.port.out.ActivationTokenGenerator;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import com.axioma.loyalty.domain.port.out.LoginAttemptStore;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ActivationHandlersTest {

    private static final String TOKEN = "tok-valido";
    private static final String IP = "203.0.113.7";
    private static final ActivationTarget TARGET =
            new ActivationTarget(TENANT_ID, USER_ID, "mi-cafe", "Mi Café", "duena");
    private static final UserAccount PENDING =
            new UserAccount(USER_ID, TENANT_ID, "Dueña", "duena", null, UserRole.OWNER, true);

    private final ActivationAttemptStore attempts = mock(ActivationAttemptStore.class);
    private final ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
    private final ActivationTokenGenerator generator = mock(ActivationTokenGenerator.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SessionRevoker revoker = mock(SessionRevoker.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final LoginAttemptStore loginAttempts = mock(LoginAttemptStore.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final RefreshTokenGenerator refreshTokens = mock(RefreshTokenGenerator.class);

    private final ActivationThrottle throttle =
            new ActivationThrottle(attempts, new ActivationProperties(null, 72, 5, 20, 15));
    private final ActivationLookup lookup = new ActivationLookup(generator, tokens);
    private final InspectActivationHandler inspect = new InspectActivationHandler(throttle, lookup);
    private final SessionStarter sessionStarter = new SessionStarter(loginAttempts, sessions, refreshTokens,
            tokenIssuer, new AuthProperties("x".repeat(32), 900, 30, 5, 15),
            Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));
    private final ActivateAccountHandler activate = new ActivateAccountHandler(tokenIssuer, throttle, lookup,
            new ActivateAccountValidator(new PasswordPolicy(new SecurityProperties(8, 4))), hasher,
            new AccountActivationWriter(tokens, users, revoker), sessionStarter);

    @BeforeEach
    void defaults() {
        when(generator.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(attempts.find(anyString(), anyInt())).thenReturn(Mono.empty());
        when(attempts.recordFailure(anyString(), anyInt())).thenReturn(Mono.just(new LoginThrottle(1, 900)));
        when(attempts.clear(anyString())).thenReturn(Mono.empty());
        when(tokens.findValid(anyString(), anyString())).thenReturn(Mono.empty());
        when(tokens.findValid("mi-cafe", "hash:" + TOKEN)).thenReturn(Mono.just(TARGET));
        when(tokens.consume("mi-cafe", "hash:" + TOKEN)).thenReturn(Mono.just(TARGET));
        when(tokens.invalidateAll(any(), any())).thenReturn(Mono.empty());
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.just(PENDING));
        when(users.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(revoker.revokeAllByUser(TENANT_ID, USER_ID)).thenReturn(Mono.just(2L));
        when(hasher.hash(anyString())).thenReturn(Mono.just("$2a$nuevo"));
        when(tokenIssuer.ensureReady()).thenReturn(Mono.empty());
        when(tokenIssuer.issue(any())).thenReturn(Mono.just(new IssuedAccessToken("access", 900)));
        when(refreshTokens.generate()).thenReturn("refresh");
        when(refreshTokens.hash(anyString())).thenReturn("refresh-hash");
        when(loginAttempts.clear(any(), anyString())).thenReturn(Mono.empty());
        when(sessions.create(any())).thenReturn(Mono.empty());
    }

    // --- inspect ---

    @Test
    void inspectReturnsTenantAndUsername() {
        StepVerifier.create(inspect.handle(new InspectActivationQuery(" Mi-Cafe ", TOKEN, IP)))
                .expectNext(TARGET)
                .verifyComplete();
        verify(attempts, never()).recordFailure(anyString(), anyInt());
    }

    @Test
    void inspectFailuresAreAllInvalidActivationAndCount() {
        for (var query : new InspectActivationQuery[] {
                new InspectActivationQuery("mi-cafe", "otro", IP),     // inexistente, vencido, usado, inactivo
                new InspectActivationQuery("otro-cafe", TOKEN, IP),    // de otro negocio
                new InspectActivationQuery("MAL slug", TOKEN, IP),     // slug imposible
                new InspectActivationQuery(null, null, null)}) {       // sin datos
            StepVerifier.create(inspect.handle(query))
                    .expectErrorSatisfies(hasCode(ErrorCode.INVALID_ACTIVATION)).verify();
        }
        verify(attempts).recordFailure("slug-ip:mi-cafe|" + IP, 15);
        verify(attempts).recordFailure("slug:mi-cafe", 15);
        verify(attempts).recordFailure("slug:", 15); // sin IP solo cuenta por slug
    }

    @Test
    void inspectIsBlockedWithoutLookingUpOnceLimitIsReached() {
        when(attempts.find("slug:mi-cafe", 15)).thenReturn(Mono.just(new LoginThrottle(20, 300)));

        StepVerifier.create(inspect.handle(new InspectActivationQuery("mi-cafe", TOKEN, IP)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TooManyAttemptsException.class);
                    assertThat(((TooManyAttemptsException) error).getRetryAfterSeconds()).isEqualTo(300);
                })
                .verify();
        verifyNoInteractions(tokens);
    }

    @Test
    void failureThatReachesTheLimitAnswers429() {
        when(attempts.recordFailure("slug-ip:mi-cafe|" + IP, 15)).thenReturn(Mono.just(new LoginThrottle(5, 600)));

        StepVerifier.create(inspect.handle(new InspectActivationQuery("mi-cafe", "otro", IP)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TooManyAttemptsException.class);
                    assertThat(((TooManyAttemptsException) error).getRetryAfterSeconds()).isEqualTo(600);
                })
                .verify();
    }

    // --- activate ---

    @Test
    void activateStoresHashRevokesSessionsAndStartsSession() {
        StepVerifier.create(activate.handle(new ActivateAccountCommand("mi-cafe", TOKEN, "nuevaClave1", IP)))
                .assertNext(session -> {
                    assertThat(session.accessToken()).isEqualTo("access");
                    assertThat(session.refreshToken()).isEqualTo("refresh");
                    assertThat(session.owner().tenantSlug()).isEqualTo("mi-cafe");
                    assertThat(session.owner().tenantName()).isEqualTo("Mi Café");
                    assertThat(session.owner().role()).isEqualTo(UserRole.OWNER);
                })
                .verifyComplete();

        var order = inOrder(tokens, users, revoker, sessions);
        order.verify(tokens).consume("mi-cafe", "hash:" + TOKEN);
        order.verify(users).update(PENDING.withCredentialHash("$2a$nuevo"));
        order.verify(revoker).revokeAllByUser(TENANT_ID, USER_ID);
        order.verify(tokens).invalidateAll(TENANT_ID, USER_ID);
        order.verify(sessions).create(any());
        verify(attempts).clear("slug-ip:mi-cafe|" + IP);
        verify(attempts).clear("slug:mi-cafe");
    }

    @Test
    void activateRejectsWeakPasswordWithTheSamePolicyWithoutConsumingTheLink() {
        for (String weak : new String[] {null, "corta", "ñ".repeat(37)}) {
            StepVerifier.create(activate.handle(new ActivateAccountCommand("mi-cafe", TOKEN, weak, IP)))
                    .expectErrorSatisfies(hasCode(ErrorCode.WEAK_PASSWORD)).verify();
        }
        verify(tokens, never()).consume(anyString(), anyString());
        verifyNoInteractions(hasher, users, revoker, sessions);
        verify(attempts, never()).recordFailure(anyString(), anyInt());
    }

    @Test
    void activateWithInvalidLinkIsInvalidActivationEvenWithWeakPassword() {
        StepVerifier.create(activate.handle(new ActivateAccountCommand("mi-cafe", "otro", "x", IP)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_ACTIVATION)).verify();
        verifyNoInteractions(hasher, users, revoker);
    }

    @Test
    void activateLosingTheRaceIsInvalidActivation() {
        when(tokens.consume("mi-cafe", "hash:" + TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(activate.handle(new ActivateAccountCommand("mi-cafe", TOKEN, "nuevaClave1", IP)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_ACTIVATION)).verify();
        verify(users, never()).update(any());
        verifyNoInteractions(revoker, sessions);
    }

    @Test
    void activateDoesNotConsumeTheLinkWhenSessionsAreUnavailable() {
        when(tokenIssuer.ensureReady()).thenReturn(Mono.error(new LoyaltyException(ErrorCode.AUTH_UNAVAILABLE)));

        StepVerifier.create(activate.handle(new ActivateAccountCommand("mi-cafe", TOKEN, "nuevaClave1", IP)))
                .expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
        verifyNoInteractions(tokens, attempts);
    }

    @Test
    void commandsNeverPrintTokenOrPassword() {
        assertThat(new ActivateAccountCommand("mi-cafe", TOKEN, "nuevaClave1", IP).toString())
                .doesNotContain(TOKEN).doesNotContain("nuevaClave1");
        assertThat(new InspectActivationQuery("mi-cafe", TOKEN, IP).toString()).doesNotContain(TOKEN);
    }
}

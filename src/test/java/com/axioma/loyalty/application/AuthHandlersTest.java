package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.TENANT_ID;
import static com.axioma.loyalty.application.AdminFixtures.USER_ID;
import static com.axioma.loyalty.application.AdminFixtures.hasCode;
import static com.axioma.loyalty.application.AdminFixtures.tenant;
import static com.axioma.loyalty.application.AdminFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.command.LoginCommand;
import com.axioma.loyalty.application.command.LogoutCommand;
import com.axioma.loyalty.application.command.RefreshSessionCommand;
import com.axioma.loyalty.application.handler.AuthenticateStaffHandler;
import com.axioma.loyalty.application.handler.LoginHandler;
import com.axioma.loyalty.application.handler.LogoutHandler;
import com.axioma.loyalty.application.handler.RefreshSessionHandler;
import com.axioma.loyalty.application.query.AuthenticateStaffQuery;
import com.axioma.loyalty.application.validator.LoginValidator;
import com.axioma.loyalty.config.AuthProperties;
import com.axioma.loyalty.domain.model.AccessTokenClaims;
import com.axioma.loyalty.domain.model.IssuedAccessToken;
import com.axioma.loyalty.domain.model.LoginThrottle;
import com.axioma.loyalty.domain.model.NewSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.TooManyAttemptsException;
import com.axioma.loyalty.domain.port.out.AccessTokenDecoder;
import com.axioma.loyalty.domain.port.out.LoginAttemptStore;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.RefreshTokenGenerator;
import com.axioma.loyalty.domain.port.out.SessionRepository;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthHandlersTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final AccessTokenDecoder decoder = mock(AccessTokenDecoder.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final LoginAttemptStore attempts = mock(LoginAttemptStore.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final RefreshTokenGenerator refreshTokens = mock(RefreshTokenGenerator.class);
    private final AuthProperties properties = new AuthProperties("x".repeat(32), 900, 30, 5, 15);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final LoginHandler login = new LoginHandler(new LoginValidator(), tokenIssuer, tenants, users, hasher,
            attempts, sessions, refreshTokens, properties, clock);
    private final RefreshSessionHandler refresh =
            new RefreshSessionHandler(tokenIssuer, sessions, refreshTokens, properties, clock);
    private final LogoutHandler logout = new LogoutHandler(sessions, refreshTokens);
    private final AuthenticateStaffHandler authenticate = new AuthenticateStaffHandler(decoder, sessions);

    @BeforeEach
    void defaults() {
        when(tokenIssuer.ensureReady()).thenReturn(Mono.empty());
        when(tokenIssuer.issue(any())).thenReturn(Mono.just(new IssuedAccessToken("access", 900)));
        when(refreshTokens.generate()).thenReturn("nuevo-refresh");
        when(refreshTokens.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(tenants.findBySlug(new Slug("mi-cafe"))).thenReturn(Mono.just(tenant(true)));
        when(attempts.find(any(), anyString(), eq(15))).thenReturn(Mono.empty());
        when(attempts.clear(any(), anyString())).thenReturn(Mono.empty());
        when(attempts.recordFailure(any(), anyString(), eq(15))).thenReturn(Mono.just(new LoginThrottle(1, 900)));
        when(sessions.create(any())).thenReturn(Mono.empty());
        when(hasher.matches(anyString(), any())).thenReturn(Mono.just(false));
    }

    // --- login ---

    @Test
    void loginCreatesSessionWithHashedRefreshToken() {
        when(users.findByUsername(TENANT_ID, "ana")).thenReturn(Mono.just(user()));
        when(hasher.matches("secreto123", "$2a$hash")).thenReturn(Mono.just(true));

        StepVerifier.create(login.handle(new LoginCommand(" Mi-Cafe ", " ANA ", "secreto123")))
                .assertNext(auth -> {
                    assertThat(auth.accessToken()).isEqualTo("access");
                    assertThat(auth.refreshToken()).isEqualTo("nuevo-refresh");
                    assertThat(auth.owner().role()).isEqualTo(UserRole.CASHIER);
                    assertThat(auth.owner().tenantSlug()).isEqualTo("mi-cafe");
                })
                .verifyComplete();

        var session = ArgumentCaptor.forClass(NewSession.class);
        verify(sessions).create(session.capture());
        assertThat(session.getValue().refreshTokenHash()).isEqualTo("hash:nuevo-refresh");
        assertThat(session.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(30L * 86400));
        verify(attempts).clear(TENANT_ID, "ana");
    }

    @Test
    void wrongPasswordRecordsFailureWithSingleBcrypt() {
        when(users.findByUsername(TENANT_ID, "ana")).thenReturn(Mono.just(user()));

        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "ana", "incorrecta")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CREDENTIALS)).verify();
        verify(hasher, times(1)).matches(anyString(), any());
        verify(attempts).recordFailure(TENANT_ID, "ana", 15);
        verify(sessions, never()).create(any());
    }

    @Test
    void unknownOrInactiveUserRunsDummyBcryptAndSameError() {
        when(users.findByUsername(TENANT_ID, "nadie")).thenReturn(Mono.empty());
        var inactive = new UserAccount(USER_ID, TENANT_ID, "Ana", "ana", "$2a$hash", UserRole.CASHIER, false);
        when(users.findByUsername(TENANT_ID, "ana")).thenReturn(Mono.just(inactive));

        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "nadie", "secreto123")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CREDENTIALS)).verify();
        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "ana", "secreto123")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CREDENTIALS)).verify();
        verify(hasher, times(2)).matches(eq("secreto123"), isNull());
    }

    @Test
    void unknownOrInactiveTenantRunsDummyBcryptAndSameError() {
        when(tenants.findBySlug(new Slug("no-existe"))).thenReturn(Mono.empty());
        when(tenants.findBySlug(new Slug("cerrado"))).thenReturn(Mono.just(tenant(false)));

        for (String slug : new String[] {"no-existe", "cerrado", "Slug Inválido"}) {
            StepVerifier.create(login.handle(new LoginCommand(slug, "ana", "secreto123")))
                    .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CREDENTIALS)).verify();
        }
        verify(hasher, times(3)).matches(eq("secreto123"), isNull());
        verifyNoInteractions(users, attempts);
    }

    @Test
    void failureThatReachesTheLimitLocks() {
        when(users.findByUsername(TENANT_ID, "ana")).thenReturn(Mono.just(user()));
        when(attempts.recordFailure(TENANT_ID, "ana", 15)).thenReturn(Mono.just(new LoginThrottle(5, 840)));

        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "ana", "incorrecta")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TooManyAttemptsException.class);
                    assertThat(((TooManyAttemptsException) error).getRetryAfterSeconds()).isEqualTo(840);
                })
                .verify();
    }

    @Test
    void lockedAccountIsRejectedWithoutCheckingPassword() {
        when(attempts.find(TENANT_ID, "ana", 15)).thenReturn(Mono.just(new LoginThrottle(5, 300)));

        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "ana", "secreto123")))
                .expectErrorSatisfies(hasCode(ErrorCode.TOO_MANY_ATTEMPTS)).verify();
        verifyNoInteractions(hasher, users);
    }

    @Test
    void loginUnavailableWithoutSigningKey() {
        when(tokenIssuer.ensureReady()).thenReturn(Mono.error(new LoyaltyException(ErrorCode.AUTH_UNAVAILABLE)));

        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", "ana", "secreto123")))
                .expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
        verifyNoInteractions(tenants, hasher);
    }

    @Test
    void loginRejectsMissingFields() {
        StepVerifier.create(login.handle(new LoginCommand("mi-cafe", " ", "secreto123")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_REQUEST)).verify();
    }

    // --- refresh ---

    @Test
    void refreshRotatesToken() {
        var owner = new SessionOwner(SESSION_ID, TENANT_ID, USER_ID, "Ana", UserRole.OWNER, "mi-cafe", "Mi Café");
        when(sessions.rotate("hash:viejo", "hash:nuevo-refresh", NOW.plusSeconds(30L * 86400)))
                .thenReturn(Mono.just(owner));

        StepVerifier.create(refresh.handle(new RefreshSessionCommand("viejo")))
                .assertNext(auth -> {
                    assertThat(auth.refreshToken()).isEqualTo("nuevo-refresh");
                    assertThat(auth.owner()).isEqualTo(owner);
                })
                .verifyComplete();
        verify(sessions, never()).revokeByRefreshTokenHash(anyString());
    }

    @Test
    void refreshWithUnknownOrReusedTokenRevokesItsSession() {
        when(sessions.rotate(eq("hash:robado"), anyString(), any())).thenReturn(Mono.empty());
        when(sessions.revokeByRefreshTokenHash("hash:robado")).thenReturn(Mono.just(1L));

        StepVerifier.create(refresh.handle(new RefreshSessionCommand("robado")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SESSION)).verify();
        verify(sessions).revokeByRefreshTokenHash("hash:robado");
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void refreshDoesNotRotateWhenKeyIsMissing() {
        when(tokenIssuer.ensureReady()).thenReturn(Mono.error(new LoyaltyException(ErrorCode.AUTH_UNAVAILABLE)));

        StepVerifier.create(refresh.handle(new RefreshSessionCommand("viejo")))
                .expectErrorSatisfies(hasCode(ErrorCode.AUTH_UNAVAILABLE)).verify();
        verifyNoInteractions(sessions);
    }

    // --- logout ---

    @Test
    void logoutRevokesAndIsIdempotent() {
        when(sessions.revokeByRefreshTokenHash("hash:t")).thenReturn(Mono.just(0L));

        StepVerifier.create(logout.handle(new LogoutCommand("t"))).verifyComplete();
        StepVerifier.create(logout.handle(new LogoutCommand(null))).verifyComplete();
        verify(sessions).revokeByRefreshTokenHash("hash:t");
    }

    // --- access token ---

    @Test
    void authenticatesActiveSessionWithCurrentRole() {
        when(decoder.decode("jwt")).thenReturn(Mono.just(
                new AccessTokenClaims(USER_ID, TENANT_ID, UserRole.CASHIER, SESSION_ID)));
        when(sessions.findActiveRole(SESSION_ID, TENANT_ID, USER_ID)).thenReturn(Mono.just(UserRole.OWNER));

        StepVerifier.create(authenticate.handle(new AuthenticateStaffQuery("jwt")))
                .expectNext(new StaffPrincipal(USER_ID, TENANT_ID, UserRole.OWNER, SESSION_ID))
                .verifyComplete();
    }

    @Test
    void rejectsValidTokenOfRevokedSession() {
        when(decoder.decode("jwt")).thenReturn(Mono.just(
                new AccessTokenClaims(USER_ID, TENANT_ID, UserRole.CASHIER, SESSION_ID)));
        when(sessions.findActiveRole(SESSION_ID, TENANT_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(authenticate.handle(new AuthenticateStaffQuery("jwt")))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
    }

    @Test
    void rejectsMissingAccessToken() {
        StepVerifier.create(authenticate.handle(new AuthenticateStaffQuery(null)))
                .expectErrorSatisfies(hasCode(ErrorCode.UNAUTHORIZED)).verify();
        verifyNoInteractions(decoder);
    }

    @Test
    void ownerSatisfiesCashierButNotTheOtherWayAround() {
        var owner = new StaffPrincipal(USER_ID, TENANT_ID, UserRole.OWNER, SESSION_ID);
        var cashier = new StaffPrincipal(USER_ID, TENANT_ID, UserRole.CASHIER, SESSION_ID);

        assertThat(owner.hasRole(UserRole.CASHIER)).isTrue();
        assertThat(cashier.hasRole(UserRole.CASHIER)).isTrue();
        assertThat(cashier.hasRole(UserRole.OWNER)).isFalse();
    }

    @Test
    void commandsNeverPrintSecrets() {
        assertThat(new LoginCommand("mi-cafe", "ana", "SuperSecreta1").toString()).doesNotContain("SuperSecreta1");
        assertThat(new RefreshSessionCommand("tok-123").toString()).doesNotContain("tok-123");
        assertThat(new LogoutCommand("tok-123").toString()).doesNotContain("tok-123");
        assertThat(new AuthenticateStaffQuery("jwt-123").toString()).doesNotContain("jwt-123");
    }
}

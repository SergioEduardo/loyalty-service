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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.command.CreateTenantCommand;
import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.application.command.DeactivateUserCommand;
import com.axioma.loyalty.application.command.IssueActivationLinkCommand;
import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.application.handler.ActivationLinkIssuer;
import com.axioma.loyalty.application.handler.ActiveTenantGuard;
import com.axioma.loyalty.application.handler.CreateTenantHandler;
import com.axioma.loyalty.application.handler.CreateUserHandler;
import com.axioma.loyalty.application.handler.DeactivateUserHandler;
import com.axioma.loyalty.application.handler.IssueActivationLinkHandler;
import com.axioma.loyalty.application.handler.ResetPasswordHandler;
import com.axioma.loyalty.application.handler.TenantCreationWriter;
import com.axioma.loyalty.application.validator.CreateTenantValidator;
import com.axioma.loyalty.application.validator.CreateUserValidator;
import com.axioma.loyalty.application.validator.PasswordPolicy;
import com.axioma.loyalty.application.validator.ResetPasswordValidator;
import com.axioma.loyalty.config.ActivationProperties;
import com.axioma.loyalty.config.SecurityProperties;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.ActivationTokenGenerator;
import com.axioma.loyalty.domain.port.out.ActivationTokenRepository;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.SessionRevoker;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import com.axioma.loyalty.domain.port.out.UserRepository;
import com.axioma.loyalty.support.OutboxFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminHandlersTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TenantEventOutbox outbox = mock(TenantEventOutbox.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final SessionRevoker sessions = mock(SessionRevoker.class);
    private final PasswordPolicy policy = new PasswordPolicy(new SecurityProperties(8, 4));
    private final ActiveTenantGuard guard = new ActiveTenantGuard(tenants);
    private final ActivationTokenRepository activationTokens = mock(ActivationTokenRepository.class);
    private final ActivationTokenGenerator tokenGenerator = mock(ActivationTokenGenerator.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ActivationLinkIssuer linkIssuer = new ActivationLinkIssuer(tokenGenerator, activationTokens, users,
            new ActivationProperties("https://web.example.com/", 72, 5, 20, 15), clock);

    private final CreateTenantHandler createTenant =
            new CreateTenantHandler(new CreateTenantValidator(), new TenantCreationWriter(tenants, outbox),
                    OutboxFixtures.disabledFastPath());
    private final CreateUserHandler createUser =
            new CreateUserHandler(new CreateUserValidator(policy), guard, hasher, users, linkIssuer);
    private final DeactivateUserHandler deactivate = new DeactivateUserHandler(guard, users, sessions);
    private final ResetPasswordHandler reset =
            new ResetPasswordHandler(new ResetPasswordValidator(policy), guard, hasher, users, sessions,
                    activationTokens);
    private final IssueActivationLinkHandler issueLink = new IssueActivationLinkHandler(guard, users, linkIssuer);

    @BeforeEach
    void defaults() {
        when(tokenGenerator.generate()).thenReturn("token-secreto");
        when(tokenGenerator.hash("token-secreto")).thenReturn("hash-del-token");
        when(activationTokens.create(any(), any(), anyString(), any())).thenReturn(Mono.empty());
        when(activationTokens.invalidateAll(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void createTenantWritesTenantChangedEvent() {
        when(tenants.create(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outbox.append(any())).thenReturn(Mono.just(1L));

        var command = new CreateTenantCommand("mi-cafe", "Mi Café", null, "#112233", 10, "Café gratis", null, null);
        StepVerifier.create(createTenant.handle(command))
                .assertNext(tenant -> {
                    assertThat(tenant.slug().value()).isEqualTo("mi-cafe");
                    assertThat(tenant.undoWindowSeconds()).isEqualTo(300);
                    assertThat(tenant.active()).isTrue();
                })
                .verifyComplete();

        var event = ArgumentCaptor.forClass(TenantChanged.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().slug()).isEqualTo("mi-cafe");
        assertThat(event.getValue().pointsGoal()).isEqualTo(10);
    }

    @Test
    void createTenantDoesNotWriteEventWhenSlugIsTaken() {
        when(tenants.create(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.SLUG_ALREADY_EXISTS)));

        var command = new CreateTenantCommand("mi-cafe", "Mi Café", null, null, 10, "Café gratis", null, null);
        StepVerifier.create(createTenant.handle(command))
                .expectErrorSatisfies(hasCode(ErrorCode.SLUG_ALREADY_EXISTS)).verify();
        verifyNoInteractions(outbox);
    }

    @Test
    void createUserHashesPassword() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(hasher.hash("secreto123")).thenReturn(Mono.just("$2a$hash"));
        when(users.create(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createUser.handle(new CreateUserCommand(TENANT_ID, "Ana", " ANA ", "secreto123", "OWNER")))
                .assertNext(created -> {
                    assertThat(created.user().username()).isEqualTo("ana");
                    assertThat(created.user().credentialHash()).isEqualTo("$2a$hash");
                    assertThat(created.user().role()).isEqualTo(UserRole.OWNER);
                    assertThat(created.activation()).isNull();
                })
                .verifyComplete();
        verifyNoInteractions(activationTokens);
    }

    @Test
    void createUserWithoutPasswordIsPendingAndGetsActivationLink() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.create(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createUser.handle(new CreateUserCommand(TENANT_ID, "Ana", "ana", null, "OWNER")))
                .assertNext(created -> {
                    assertThat(created.user().credentialHash()).isNull();
                    assertThat(created.user().pendingActivation()).isTrue();
                    assertThat(created.user().active()).isTrue();
                    assertThat(created.activation().token()).isEqualTo("token-secreto");
                    assertThat(created.activation().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
                    assertThat(created.activation().url())
                            .isEqualTo("https://web.example.com/caja/mi-cafe/activar#t=token-secreto");
                    assertThat(created.activation().toString()).doesNotContain("token-secreto");
                })
                .verifyComplete();
        verifyNoInteractions(hasher);
        var userId = ArgumentCaptor.forClass(UUID.class);
        verify(activationTokens).create(eq(TENANT_ID), userId.capture(), eq("hash-del-token"),
                eq(NOW.plus(Duration.ofHours(72))));
    }

    @Test
    void createUserRejectsEmptyPasswordInsteadOfMakingItPending() {
        StepVerifier.create(createUser.handle(new CreateUserCommand(TENANT_ID, "Ana", "ana", "", "OWNER")))
                .expectErrorSatisfies(hasCode(ErrorCode.WEAK_PASSWORD)).verify();
        verifyNoInteractions(users, activationTokens);
    }

    @Test
    void activationLinkHasNoUrlWithoutWebBaseUrl() {
        var issuer = new ActivationLinkIssuer(tokenGenerator, activationTokens, users,
                new ActivationProperties("  ", 72, 5, 20, 15), clock);

        StepVerifier.create(issuer.reissue(tenant(true), user()))
                .assertNext(link -> {
                    assertThat(link.token()).isEqualTo("token-secreto");
                    assertThat(link.url()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void issueActivationLinkInvalidatesPreviousLinks() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.just(user()));

        StepVerifier.create(issueLink.handle(new IssueActivationLinkCommand(TENANT_ID, USER_ID)))
                .assertNext(link -> assertThat(link.token()).isEqualTo("token-secreto"))
                .verifyComplete();

        var order = inOrder(activationTokens);
        order.verify(activationTokens).invalidateAll(TENANT_ID, USER_ID);
        order.verify(activationTokens).create(TENANT_ID, USER_ID, "hash-del-token", NOW.plus(Duration.ofHours(72)));
    }

    @Test
    void issueActivationLinkFailsForInactiveOrMissingUserOrTenant() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.just(user().deactivate()));
        StepVerifier.create(issueLink.handle(new IssueActivationLinkCommand(TENANT_ID, USER_ID)))
                .expectErrorSatisfies(hasCode(ErrorCode.USER_INACTIVE)).verify();

        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.empty());
        StepVerifier.create(issueLink.handle(new IssueActivationLinkCommand(TENANT_ID, USER_ID)))
                .expectErrorSatisfies(hasCode(ErrorCode.USER_NOT_FOUND)).verify();

        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(false)));
        StepVerifier.create(issueLink.handle(new IssueActivationLinkCommand(TENANT_ID, USER_ID)))
                .expectErrorSatisfies(hasCode(ErrorCode.TENANT_NOT_FOUND)).verify();
        verifyNoInteractions(activationTokens);
    }

    @Test
    void createUserFailsForInactiveTenantWithoutHashing() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(false)));

        StepVerifier.create(createUser.handle(new CreateUserCommand(TENANT_ID, null, "ana", "secreto123", "OWNER")))
                .expectErrorSatisfies(hasCode(ErrorCode.TENANT_NOT_FOUND)).verify();
        verifyNoInteractions(hasher, users);
    }

    @Test
    void deactivateMarksInactiveAndRevokesSessions() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.just(user()));
        when(users.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(sessions.revokeAllByUser(TENANT_ID, USER_ID)).thenReturn(Mono.just(2L));

        StepVerifier.create(deactivate.handle(new DeactivateUserCommand(TENANT_ID, USER_ID))).verifyComplete();

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).update(saved.capture());
        assertThat(saved.getValue().active()).isFalse();
        verify(sessions).revokeAllByUser(TENANT_ID, USER_ID);
    }

    @Test
    void deactivateFailsWhenUserIsNotInTenant() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(deactivate.handle(new DeactivateUserCommand(TENANT_ID, USER_ID)))
                .expectErrorSatisfies(hasCode(ErrorCode.USER_NOT_FOUND)).verify();
        verify(users, never()).update(any());
        verifyNoInteractions(sessions);
    }

    @Test
    void resetPasswordStoresNewHashAndRevokesSessions() {
        when(tenants.findById(TENANT_ID)).thenReturn(Mono.just(tenant(true)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Mono.just(user()));
        when(hasher.hash("nuevaClave1")).thenReturn(Mono.just("$2a$nuevo"));
        when(users.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(sessions.revokeAllByUser(TENANT_ID, USER_ID)).thenReturn(Mono.just(1L));

        StepVerifier.create(reset.handle(new ResetPasswordCommand(TENANT_ID, USER_ID, "nuevaClave1"))).verifyComplete();

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).update(saved.capture());
        assertThat(saved.getValue().credentialHash()).isEqualTo("$2a$nuevo");
        verify(sessions).revokeAllByUser(TENANT_ID, USER_ID);
        verify(activationTokens).invalidateAll(TENANT_ID, USER_ID);
    }

    @Test
    void resetPasswordRejectsWeakPasswordBeforeTouchingStorage() {
        StepVerifier.create(reset.handle(new ResetPasswordCommand(TENANT_ID, USER_ID, "corta")))
                .expectErrorSatisfies(hasCode(ErrorCode.WEAK_PASSWORD)).verify();
        verifyNoInteractions(tenants, users, hasher, sessions);
    }
}

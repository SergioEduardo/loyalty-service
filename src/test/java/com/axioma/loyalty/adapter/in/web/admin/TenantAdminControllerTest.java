package com.axioma.loyalty.adapter.in.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.in.web.GlobalExceptionHandler;
import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.application.command.DeactivateUserCommand;
import com.axioma.loyalty.application.command.IssueActivationLinkCommand;
import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.application.handler.CreateTenantHandler;
import com.axioma.loyalty.application.handler.CreateUserHandler;
import com.axioma.loyalty.application.handler.DeactivateUserHandler;
import com.axioma.loyalty.application.handler.IssueActivationLinkHandler;
import com.axioma.loyalty.application.handler.ResetPasswordHandler;
import com.axioma.loyalty.domain.model.ActivationLink;
import com.axioma.loyalty.domain.model.CreatedUser;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class TenantAdminControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CreateTenantHandler createTenant = mock(CreateTenantHandler.class);
    private final CreateUserHandler createUser = mock(CreateUserHandler.class);
    private final DeactivateUserHandler deactivate = mock(DeactivateUserHandler.class);
    private final ResetPasswordHandler reset = mock(ResetPasswordHandler.class);
    private final IssueActivationLinkHandler issueLink = mock(IssueActivationLinkHandler.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new TenantAdminController(createTenant, createUser, deactivate, reset, issueLink))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void createsTenant() {
        when(createTenant.handle(any())).thenReturn(Mono.just(new Tenant(TENANT_ID, new Slug("mi-cafe"),
                "Mi Café", null, null, null, "#112233", 10, "Café gratis", 0, 300, true)));

        client.post().uri("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", "mi-cafe", "name", "Mi Café", "pointsGoal", 10, "rewardText", "Café gratis"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(TENANT_ID.toString())
                .jsonPath("$.slug").isEqualTo("mi-cafe")
                .jsonPath("$.undoWindowSeconds").isEqualTo(300);
    }

    @Test
    void returns409WhenSlugIsTaken() {
        when(createTenant.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.SLUG_ALREADY_EXISTS)));

        client.post().uri("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("slug", "mi-cafe"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("SLUG_ALREADY_EXISTS");
    }

    @Test
    void createsUserWithoutExposingTheHash() {
        when(createUser.handle(any())).thenReturn(Mono.just(new CreatedUser(
                new UserAccount(USER_ID, TENANT_ID, "Ana", "ana", "$2a$hash", UserRole.CASHIER, true), null)));

        client.post().uri("/api/admin/tenants/{t}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ana", "password", "secreto123", "role", "CASHIER"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("ana")
                .jsonPath("$.role").isEqualTo("CASHIER")
                .jsonPath("$.credentialHash").doesNotExist()
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.activation").doesNotExist();
    }

    @Test
    void createsPendingUserWithActivationLink() {
        var expiresAt = Instant.parse("2026-10-16T12:00:00Z");
        when(createUser.handle(new CreateUserCommand(TENANT_ID, null, "ana", null, "OWNER")))
                .thenReturn(Mono.just(new CreatedUser(
                        new UserAccount(USER_ID, TENANT_ID, "ana", "ana", null, UserRole.OWNER, true),
                        new ActivationLink("tok", expiresAt, "https://web/caja/mi-cafe/activar#t=tok"))));

        client.post().uri("/api/admin/tenants/{t}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ana", "role", "OWNER"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.activation.token").isEqualTo("tok")
                .jsonPath("$.activation.expiresAt").isEqualTo("2026-10-16T12:00:00Z")
                .jsonPath("$.activation.activationUrl").isEqualTo("https://web/caja/mi-cafe/activar#t=tok");
    }

    @Test
    void issuesActivationLinkWithoutUrlWhenNotConfigured() {
        when(issueLink.handle(new IssueActivationLinkCommand(TENANT_ID, USER_ID))).thenReturn(Mono.just(
                new ActivationLink("tok", Instant.parse("2026-10-16T12:00:00Z"), null)));

        client.post().uri("/api/admin/tenants/{t}/users/{u}/activation-link", TENANT_ID, USER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("tok")
                .jsonPath("$.expiresAt").isEqualTo("2026-10-16T12:00:00Z")
                .jsonPath("$.activationUrl").doesNotExist();
    }

    @Test
    void activationLinkForInactiveUserIs409() {
        when(issueLink.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.USER_INACTIVE)));

        client.post().uri("/api/admin/tenants/{t}/users/{u}/activation-link", TENANT_ID, USER_ID)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("USER_INACTIVE");
    }

    @Test
    void returns409WhenUsernameIsTaken() {
        when(createUser.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.USERNAME_TAKEN)));

        client.post().uri("/api/admin/tenants/{t}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ana", "password", "secreto123", "role", "CASHIER"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void deactivatesUser() {
        when(deactivate.handle(new DeactivateUserCommand(TENANT_ID, USER_ID))).thenReturn(Mono.empty());

        client.post().uri("/api/admin/tenants/{t}/users/{u}/deactivate", TENANT_ID, USER_ID)
                .exchange()
                .expectStatus().isNoContent();
        verify(deactivate).handle(new DeactivateUserCommand(TENANT_ID, USER_ID));
    }

    @Test
    void resetsPassword() {
        when(reset.handle(new ResetPasswordCommand(TENANT_ID, USER_ID, "nuevaClave1"))).thenReturn(Mono.empty());

        client.post().uri("/api/admin/tenants/{t}/users/{u}/reset-password", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "nuevaClave1"))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void returns400ForMalformedTenantId() {
        client.post().uri("/api/admin/tenants/no-es-uuid/users/{u}/deactivate", USER_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void returns404WhenUserNotFound() {
        when(deactivate.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.USER_NOT_FOUND)));

        client.post().uri("/api/admin/tenants/{t}/users/{u}/deactivate", TENANT_ID, USER_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("USER_NOT_FOUND");
    }
}

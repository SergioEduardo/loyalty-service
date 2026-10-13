package com.axioma.loyalty.adapter.in.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.in.web.GlobalExceptionHandler;
import com.axioma.loyalty.application.handler.LoginHandler;
import com.axioma.loyalty.application.handler.LogoutHandler;
import com.axioma.loyalty.application.handler.RefreshSessionHandler;
import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.model.SessionOwner;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.TooManyAttemptsException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AuthControllerTest {

    private final LoginHandler login = mock(LoginHandler.class);
    private final RefreshSessionHandler refresh = mock(RefreshSessionHandler.class);
    private final LogoutHandler logout = mock(LogoutHandler.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new AuthController(login, refresh, logout))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final Map<String, String> CREDENTIALS =
            Map.of("slug", "demo-cafe", "username", "caja1", "password", "secreto123");

    @Test
    void loginReturnsTokensUserAndTenant() {
        var owner = new SessionOwner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Caja 1",
                UserRole.CASHIER, "demo-cafe", "Demo Café");
        when(login.handle(any())).thenReturn(Mono.just(new AuthSession("access", "refresh", 900, owner)));

        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue(CREDENTIALS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("access")
                .jsonPath("$.refreshToken").isEqualTo("refresh")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(900)
                .jsonPath("$.user.id").isEqualTo(owner.userId().toString())
                .jsonPath("$.user.name").isEqualTo("Caja 1")
                .jsonPath("$.user.role").isEqualTo("CASHIER")
                .jsonPath("$.tenant.slug").isEqualTo("demo-cafe")
                .jsonPath("$.tenant.name").isEqualTo("Demo Café");
    }

    @Test
    void invalidCredentialsIs401() {
        when(login.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.INVALID_CREDENTIALS)));

        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue(CREDENTIALS)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void tooManyAttemptsIs429WithRetryAfter() {
        when(login.handle(any())).thenReturn(Mono.error(new TooManyAttemptsException(120)));

        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue(CREDENTIALS)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "120")
                .expectBody().jsonPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void missingSigningKeyIs503() {
        when(login.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.AUTH_UNAVAILABLE)));

        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue(CREDENTIALS)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE");
    }

    @Test
    void invalidRefreshIs401InvalidSession() {
        when(refresh.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.INVALID_SESSION)));

        client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "viejo"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_SESSION");
    }

    @Test
    void logoutIs204EvenWithoutBody() {
        when(logout.handle(any())).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").exchange().expectStatus().isNoContent();
    }
}

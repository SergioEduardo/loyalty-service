package com.axioma.loyalty.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.handler.GetTenantIdentityHandler;
import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class TenantIdentityControllerTest {

    private final GetTenantIdentityHandler handler = mock(GetTenantIdentityHandler.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new TenantIdentityController(handler))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsPublicIdentity() {
        when(handler.handle(new GetTenantIdentityQuery("mi-cafe")))
                .thenReturn(Mono.just(new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233")));

        client.get().uri("/api/public/tenants/mi-cafe/identity")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.slug").isEqualTo("mi-cafe")
                .jsonPath("$.name").isEqualTo("Mi Café")
                .jsonPath("$.brandColor").isEqualTo("#112233");
    }

    @Test
    void returns404WhenTenantDoesNotExist() {
        when(handler.handle(new GetTenantIdentityQuery("cerrado")))
                .thenReturn(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));

        client.get().uri("/api/public/tenants/cerrado/identity")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_NOT_FOUND");
    }

    @Test
    void returns400WhenSlugIsInvalid() {
        when(handler.handle(new GetTenantIdentityQuery("MAL")))
                .thenReturn(Mono.error(new LoyaltyException(ErrorCode.INVALID_SLUG)));

        client.get().uri("/api/public/tenants/MAL/identity")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_SLUG");
    }

    @Test
    void hidesTechnicalErrors() {
        when(handler.handle(new GetTenantIdentityQuery("mi-cafe")))
                .thenReturn(Mono.error(new IllegalStateException("password=secreto")));

        client.get().uri("/api/public/tenants/mi-cafe/identity")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo(ErrorCode.INTERNAL_ERROR.defaultMessage());
    }
}

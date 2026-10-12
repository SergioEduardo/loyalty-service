package com.axioma.loyalty.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.handler.GetTenantIdentityHandler;
import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.application.validator.GetTenantIdentityValidator;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantIdentityReader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetTenantIdentityHandlerTest {

    private final TenantIdentityReader reader = mock(TenantIdentityReader.class);
    private final GetTenantIdentityHandler handler =
            new GetTenantIdentityHandler(new GetTenantIdentityValidator(), reader);

    @Test
    void returnsIdentityWhenTenantIsActive() {
        var identity = new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233");
        when(reader.findActiveBySlug("mi-cafe")).thenReturn(Mono.just(identity));

        StepVerifier.create(handler.handle(new GetTenantIdentityQuery("mi-cafe")))
                .expectNext(identity)
                .verifyComplete();
    }

    @Test
    void failsWithTenantNotFoundWhenReaderIsEmpty() {
        when(reader.findActiveBySlug("cerrado")).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(new GetTenantIdentityQuery("cerrado")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LoyaltyException.class);
                    assertThat(((LoyaltyException) error).getCode()).isEqualTo(ErrorCode.TENANT_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void doesNotQueryWhenSlugIsInvalid() {
        StepVerifier.create(handler.handle(new GetTenantIdentityQuery("Mi Café")))
                .expectErrorSatisfies(error ->
                        assertThat(((LoyaltyException) error).getCode()).isEqualTo(ErrorCode.INVALID_SLUG))
                .verify();

        verifyNoInteractions(reader);
    }
}

package com.axioma.loyalty.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.handler.GetTenantIdentityHandler;
import com.axioma.loyalty.application.handler.GetTenantManifestHandler;
import com.axioma.loyalty.application.query.GetTenantManifestQuery;
import com.axioma.loyalty.application.validator.GetTenantIdentityValidator;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.model.TenantManifest;
import com.axioma.loyalty.domain.port.out.TenantIdentityReader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetTenantManifestHandlerTest {

    private final TenantIdentityReader reader = mock(TenantIdentityReader.class);
    private final GetTenantManifestHandler handler = new GetTenantManifestHandler(
            new GetTenantIdentityHandler(new GetTenantIdentityValidator(), reader));

    @Test
    void buildsManifestFromPublicIdentity() {
        var identity = new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233");
        when(reader.findActiveBySlug("mi-cafe")).thenReturn(Mono.just(identity));

        StepVerifier.create(handler.handle(new GetTenantManifestQuery("mi-cafe")))
                .expectNext(TenantManifest.of(identity))
                .verifyComplete();
    }

    @Test
    void isEmptyWhenTenantDoesNotExistOrIsInactive() {
        when(reader.findActiveBySlug("cerrado")).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(new GetTenantManifestQuery("cerrado"))).verifyComplete();
    }

    @Test
    void isEmptyWithoutQueryingWhenSlugIsInvalid() {
        StepVerifier.create(handler.handle(new GetTenantManifestQuery("Mi Café"))).verifyComplete();

        verifyNoInteractions(reader);
    }

    @Test
    void propagatesTechnicalErrors() {
        when(reader.findActiveBySlug("mi-cafe")).thenReturn(Mono.error(new IllegalStateException("db caída")));

        StepVerifier.create(handler.handle(new GetTenantManifestQuery("mi-cafe")))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                .verify();
    }
}

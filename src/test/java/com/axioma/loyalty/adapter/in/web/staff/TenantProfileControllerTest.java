package com.axioma.loyalty.adapter.in.web.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.in.web.GlobalExceptionHandler;
import com.axioma.loyalty.adapter.in.web.auth.StaffAuthFilter;
import com.axioma.loyalty.application.command.RemoveTenantLogoCommand;
import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.application.handler.GetTenantProfileHandler;
import com.axioma.loyalty.application.handler.RemoveTenantLogoHandler;
import com.axioma.loyalty.application.handler.UpdateTenantIdentityHandler;
import com.axioma.loyalty.application.handler.UpdateTenantSettingsHandler;
import com.axioma.loyalty.application.handler.UploadTenantLogoHandler;
import com.axioma.loyalty.application.query.GetTenantProfileQuery;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.TenantProfile;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.support.TestImages;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

/** Traducción HTTP ↔ casos de uso. Autenticación y roles se prueban en TenantProfileFlowIT. */
class TenantProfileControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final StaffPrincipal OWNER =
            new StaffPrincipal(UUID.randomUUID(), TENANT_ID, UserRole.OWNER, UUID.randomUUID());
    private static final TenantProfile PROFILE = new TenantProfile("mi-cafe", "Mi Café", "Hola",
            "https://storage.googleapis.com/b/tenants/x/logo-1.png", "#112233", 10, "Café gratis", 0, 300);

    private final GetTenantProfileHandler get = mock(GetTenantProfileHandler.class);
    private final UpdateTenantIdentityHandler identity = mock(UpdateTenantIdentityHandler.class);
    private final UpdateTenantSettingsHandler settings = mock(UpdateTenantSettingsHandler.class);
    private final UploadTenantLogoHandler upload = mock(UploadTenantLogoHandler.class);
    private final RemoveTenantLogoHandler remove = mock(RemoveTenantLogoHandler.class);

    private final WebTestClient client = WebTestClient
            .bindToController(new TenantProfileController(get, identity, settings, upload, remove))
            .controllerAdvice(new GlobalExceptionHandler())
            .webFilter((exchange, chain) -> {
                exchange.getAttributes().put(StaffAuthFilter.PRINCIPAL_ATTRIBUTE, OWNER);
                return chain.filter(exchange);
            })
            .build();

    @Test
    void getReturnsProfileOfTheTokenTenant() {
        when(get.handle(any())).thenReturn(Mono.just(PROFILE));

        client.get().uri("/api/staff/tenant?tenantId=" + UUID.randomUUID() + "&slug=otro").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.slug").isEqualTo("mi-cafe")
                .jsonPath("$.name").isEqualTo("Mi Café")
                .jsonPath("$.description").isEqualTo("Hola")
                .jsonPath("$.logoUrl").isEqualTo(PROFILE.logoUrl())
                .jsonPath("$.brandColor").isEqualTo("#112233")
                .jsonPath("$.settings.pointsGoal").isEqualTo(10)
                .jsonPath("$.settings.rewardText").isEqualTo("Café gratis")
                .jsonPath("$.settings.cooldownSeconds").isEqualTo(0)
                .jsonPath("$.settings.undoWindowSeconds").isEqualTo(300);
        verify(get).handle(new GetTenantProfileQuery(TENANT_ID));
    }

    @Test
    void identityIgnoresTenantAndSlugFromBodyAndQuery() {
        when(identity.handle(any())).thenReturn(Mono.just(PROFILE));

        client.put().uri("/api/staff/tenant/identity?tenantId=" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Mi Café", "brandColor", "#112233",
                        "tenantId", UUID.randomUUID().toString(), "slug", "otro-negocio"))
                .exchange()
                .expectStatus().isOk();

        var command = ArgumentCaptor.forClass(UpdateTenantIdentityCommand.class);
        verify(identity).handle(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void validationErrorUsesTheServiceErrorFormat() {
        when(identity.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.INVALID_BRAND_COLOR)));

        client.put().uri("/api/staff/tenant/identity").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Mi Café", "brandColor", "azul"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BRAND_COLOR")
                .jsonPath("$.message").isEqualTo("El color debe tener el formato #RRGGBB.");
    }

    @Test
    void uploadPassesFileBytesWithTokenTenant() {
        when(upload.handle(any())).thenReturn(Mono.just(PROFILE));
        byte[] png = TestImages.png(30, 20);

        uploadLogo(png, "logo.png").expectStatus().isOk().expectBody().jsonPath("$.logoUrl").isEqualTo(PROFILE.logoUrl());

        var command = ArgumentCaptor.forClass(UploadTenantLogoCommand.class);
        verify(upload).handle(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.getValue().content()).isEqualTo(png);
    }

    @Test
    void fileOverTwoMegabytesIs413WithoutCallingTheUseCase() {
        uploadLogo(new byte[TenantLogo.MAX_BYTES + 1], "logo.png")
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.code").isEqualTo("LOGO_TOO_LARGE");
        verify(upload, never()).handle(any());
    }

    @Test
    void unsupportedFormatIs415() {
        when(upload.handle(any())).thenReturn(Mono.error(new LoyaltyException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT)));

        uploadLogo(TestImages.svg(), "logo.png")
                .expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_IMAGE_FORMAT");
    }

    @Test
    void missingFilePartIs400() {
        var body = new MultipartBodyBuilder();
        body.part("otro", "x");
        client.put().uri("/api/staff/tenant/logo").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void removeLogoIs204() {
        when(remove.handle(any())).thenReturn(Mono.empty());

        client.delete().uri("/api/staff/tenant/logo").exchange().expectStatus().isNoContent();
        verify(remove).handle(new RemoveTenantLogoCommand(TENANT_ID));
    }

    private WebTestClient.ResponseSpec uploadLogo(byte[] content, String filename) {
        var body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.IMAGE_PNG);
        return client.put().uri("/api/staff/tenant/logo").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange();
    }
}

package com.axioma.loyalty.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.application.handler.GetTenantManifestHandler;
import com.axioma.loyalty.application.query.GetTenantManifestQuery;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.model.TenantManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class TenantManifestControllerTest {

    private static final String LOGO = "https://storage.googleapis.com/b/tenants/t/logo-abc.png";

    private final GetTenantManifestHandler handler = mock(GetTenantManifestHandler.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new TenantManifestController(handler))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void servesManifestWithHeaders() {
        givenTenant(new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233"));

        client.get().uri("/caja/mi-cafe/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/manifest+json")
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .expectBody()
                .jsonPath("$.id").isEqualTo("/caja/mi-cafe")
                .jsonPath("$.name").isEqualTo("Mi Café")
                .jsonPath("$.short_name").isEqualTo("Mi Café")
                .jsonPath("$.description").isEqualTo("Bienvenido")
                .jsonPath("$.lang").isEqualTo("es-MX")
                .jsonPath("$.start_url").isEqualTo("/caja/mi-cafe")
                .jsonPath("$.scope").isEqualTo("/")
                .jsonPath("$.display").isEqualTo("standalone")
                .jsonPath("$.orientation").isEqualTo("portrait")
                .jsonPath("$.background_color").isEqualTo("#f6f4ef")
                .jsonPath("$.theme_color").isEqualTo("#f6f4ef");
    }

    @Test
    void usesDefaultIconsWithoutLogo() {
        givenTenant(new TenantIdentity("mi-cafe", "Mi Café", null, null, "#112233"));

        client.get().uri("/caja/mi-cafe/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.description").doesNotExist()
                .jsonPath("$.icons.length()").isEqualTo(2)
                .jsonPath("$.icons[0].src").isEqualTo("/icons/icon-192.png")
                .jsonPath("$.icons[0].sizes").isEqualTo("192x192")
                .jsonPath("$.icons[1].src").isEqualTo("/icons/icon-512.png")
                .jsonPath("$.icons[1].sizes").isEqualTo("512x512")
                .jsonPath("$.icons[*].type").isEqualTo(List.of("image/png", "image/png"))
                .jsonPath("$.icons[*].purpose").isEqualTo(List.of("any", "any"));
    }

    @Test
    void usesLogoAs512And192() {
        givenTenant(new TenantIdentity("mi-cafe", "Mi Café", null, LOGO, "#112233"));

        client.get().uri("/caja/mi-cafe/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.icons.length()").isEqualTo(2)
                .jsonPath("$.icons[0].src").isEqualTo(LOGO)
                .jsonPath("$.icons[0].sizes").isEqualTo("512x512")
                .jsonPath("$.icons[0].type").isEqualTo("image/png")
                .jsonPath("$.icons[0].purpose").isEqualTo("any")
                .jsonPath("$.icons[1].src").isEqualTo(LOGO)
                .jsonPath("$.icons[1].sizes").isEqualTo("192x192");
    }

    @Test
    void truncatesLongNameForShortName() {
        givenTenant(new TenantIdentity("la-esquina", "La Esquina del Sabor", null, null, "#112233"));

        client.get().uri("/caja/la-esquina/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("La Esquina del Sabor")
                .jsonPath("$.short_name").isEqualTo("La Esquina");
    }

    @Test
    void isAValidManifest() throws Exception {
        givenTenant(new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", LOGO, "#112233"));

        byte[] body = client.get().uri("/caja/mi-cafe/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();

        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.isObject()).isTrue();
        for (String field : new String[] {"id", "name", "short_name", "start_url", "scope", "display", "icons"}) {
            assertThat(json.path(field).isMissingNode()).as(field).isFalse();
        }
        assertThat(json.path("display").asText()).isIn("fullscreen", "standalone", "minimal-ui", "browser");
        assertThat(json.path("start_url").asText()).startsWith(json.path("scope").asText());
        assertThat(json.path("short_name").asText().codePointCount(0, json.path("short_name").asText().length()))
                .isLessThanOrEqualTo(12);
        assertThat(json.path("icons")).isNotEmpty();
        for (JsonNode icon : json.path("icons")) {
            assertThat(icon.path("src").asText()).isNotBlank();
            assertThat(icon.path("sizes").asText()).matches("\\d+x\\d+");
            assertThat(icon.path("type").asText()).isEqualTo("image/png");
        }
        // Instalable en Chrome: al menos un ícono de 192 y uno de 512.
        assertThat(json.findValuesAsText("sizes")).contains("192x192", "512x512");
        // Solo campos de la identidad pública: nada interno del negocio.
        assertThat(json.toString()).doesNotContain("brand", "tenantId", "logo_object_key", "points");
    }

    @Test
    void returns404WithoutBodyWhenTenantIsMissingOrInactive() {
        when(handler.handle(new GetTenantManifestQuery("cerrado"))).thenReturn(Mono.empty());

        client.get().uri("/caja/cerrado/manifest.webmanifest")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }

    private void givenTenant(TenantIdentity identity) {
        when(handler.handle(new GetTenantManifestQuery(identity.slug())))
                .thenReturn(Mono.just(TenantManifest.of(identity)));
    }
}

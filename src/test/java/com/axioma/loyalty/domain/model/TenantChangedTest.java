package com.axioma.loyalty.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Contrato del payload de TenantChanged con el wallet-service: nombres JSON y valores. El wallet-service
 * forma el id de la clase de Google con {@code slug}; quitar o renombrar un campo rompe a ese consumidor.
 */
class TenantChangedTest {

    private static final UUID TENANT_ID = UUID.fromString("3f2a6c1e-0b5d-4c3e-9a7f-1d2e3f4a5b6c");

    // Mismo builder que usa Spring Boot para el ObjectMapper de la aplicación.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void payloadKeepsFieldNamesAndValues() throws Exception {
        var tenant = new Tenant(TENANT_ID, new Slug("cafe-flujo"), "Café Flujo", "Bienvenido",
                "https://storage.googleapis.com/b/tenants/t/logo-abc.png", "tenants/t/logo-abc.png", "#112233", 8,
                "Café gratis", 60, 300, true);

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(TenantChanged.from(tenant)));

        assertThat(fieldNames(payload)).containsExactlyInAnyOrder(
                "tenantId", "slug", "name", "description", "logoUrl", "brandColor", "pointsGoal", "rewardText");
        assertThat(payload.get("tenantId").asText()).isEqualTo(TENANT_ID.toString());
        assertThat(payload.get("slug").asText()).isEqualTo("cafe-flujo");
        assertThat(payload.get("name").asText()).isEqualTo("Café Flujo");
        assertThat(payload.get("description").asText()).isEqualTo("Bienvenido");
        assertThat(payload.get("logoUrl").asText()).isEqualTo("https://storage.googleapis.com/b/tenants/t/logo-abc.png");
        assertThat(payload.get("brandColor").asText()).isEqualTo("#112233");
        assertThat(payload.get("pointsGoal").isInt()).isTrue();
        assertThat(payload.get("pointsGoal").asInt()).isEqualTo(8);
        assertThat(payload.get("rewardText").asText()).isEqualTo("Café gratis");
    }

    @Test
    void payloadKeepsNullFieldsAsNull() throws Exception {
        var tenant = new Tenant(TENANT_ID, new Slug("cafe-flujo"), "Café Flujo", null, null, null, "#112233", 8,
                "Café gratis", 0, 300, true);

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(TenantChanged.from(tenant)));

        assertThat(payload.has("description")).isTrue();
        assertThat(payload.get("description").isNull()).isTrue();
        assertThat(payload.has("logoUrl")).isTrue();
        assertThat(payload.get("logoUrl").isNull()).isTrue();
        assertThat(payload.get("slug").asText()).isEqualTo("cafe-flujo");
    }

    private static List<String> fieldNames(JsonNode node) {
        var names = new ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

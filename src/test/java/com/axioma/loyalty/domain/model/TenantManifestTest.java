package com.axioma.loyalty.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TenantManifestTest {

    @Test
    void usesTenantSlugAndPublicIdentity() {
        var manifest = TenantManifest.of(new TenantIdentity("mi-cafe", "Mi Café", "Bienvenido", null, "#112233"));

        assertThat(manifest.id()).isEqualTo("/caja/mi-cafe");
        assertThat(manifest.startUrl()).isEqualTo("/caja/mi-cafe");
        assertThat(manifest.scope()).isEqualTo("/");
        assertThat(manifest.name()).isEqualTo("Mi Café");
        assertThat(manifest.shortName()).isEqualTo("Mi Café");
        assertThat(manifest.description()).isEqualTo("Bienvenido");
        assertThat(manifest.lang()).isEqualTo("es-MX");
        assertThat(manifest.display()).isEqualTo("standalone");
        assertThat(manifest.orientation()).isEqualTo("portrait");
        assertThat(manifest.backgroundColor()).isEqualTo("#f6f4ef");
        assertThat(manifest.themeColor()).isEqualTo("#f6f4ef");
    }

    @Test
    void usesDefaultIconsWithoutLogo() {
        var manifest = TenantManifest.of(new TenantIdentity("mi-cafe", "Mi Café", null, null, "#112233"));

        assertThat(manifest.icons()).containsExactly(
                new TenantManifest.Icon("/icons/icon-192.png", "192x192", "image/png", "any"),
                new TenantManifest.Icon("/icons/icon-512.png", "512x512", "image/png", "any"));
    }

    @Test
    void declaresLogoAs512And192() {
        String logo = "https://storage.googleapis.com/b/tenants/t/logo-abc.png";
        var manifest = TenantManifest.of(new TenantIdentity("mi-cafe", "Mi Café", null, logo, "#112233"));

        assertThat(manifest.icons()).containsExactly(
                new TenantManifest.Icon(logo, "512x512", "image/png", "any"),
                new TenantManifest.Icon(logo, "192x192", "image/png", "any"));
        assertThat(manifest.icons()).extracting(TenantManifest.Icon::src).isEqualTo(List.of(logo, logo));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mi Café|Mi Café",
            "Doce letras|Doce letras",
            "Cafetería Luna|Cafetería",
            "La Esquina del Sabor|La Esquina",
            "  Pan   de   Casa  Grande |Pan de Casa",
            "Panaderíadelcentro Norte|Panaderíadel",
            "Café ☕☕☕☕☕☕☕ Norte|Café ☕☕☕☕☕☕☕"
    })
    void shortNameKeepsWholeWordsWithinTwelveCharacters(String name, String expected) {
        String shortName = TenantManifest.shortName(name);

        assertThat(shortName).isEqualTo(expected);
        assertThat(shortName.codePointCount(0, shortName.length())).isLessThanOrEqualTo(12);
    }

    @Test
    void shortNameNeverSplitsASurrogatePair() {
        String shortName = TenantManifest.shortName("😀".repeat(20));

        assertThat(shortName).isEqualTo("😀".repeat(12));
    }
}

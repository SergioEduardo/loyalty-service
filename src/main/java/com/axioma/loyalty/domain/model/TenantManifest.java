package com.axioma.loyalty.domain.model;

import java.util.List;

/**
 * Manifest de la PWA de caja para un negocio: la app instalada arranca en /caja/{slug}.
 * Solo usa datos de la identidad pública.
 */
public record TenantManifest(
        String id,
        String name,
        String shortName,
        String description,
        String lang,
        String startUrl,
        String scope,
        String display,
        String orientation,
        String backgroundColor,
        String themeColor,
        List<Icon> icons) {

    public static final int SHORT_NAME_MAX_LENGTH = 12;
    private static final String APP_COLOR = "#f6f4ef";
    private static final String PNG = "image/png";
    private static final String PURPOSE_ANY = "any";

    public record Icon(String src, String sizes, String type, String purpose) {
    }

    public static TenantManifest of(TenantIdentity identity) {
        String path = "/caja/" + identity.slug();
        return new TenantManifest(
                path,
                identity.name(),
                shortName(identity.name()),
                identity.description(),
                "es-MX",
                path,
                "/",
                "standalone",
                "portrait",
                APP_COLOR,
                APP_COLOR,
                icons(identity.logoUrl()));
    }

    /** El logo ya es un PNG de 512 × 512; se declara también como 192 × 192 para que el navegador lo escale. */
    private static List<Icon> icons(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return List.of(
                    new Icon("/icons/icon-192.png", "192x192", PNG, PURPOSE_ANY),
                    new Icon("/icons/icon-512.png", "512x512", PNG, PURPOSE_ANY));
        }
        return List.of(
                new Icon(logoUrl, "512x512", PNG, PURPOSE_ANY),
                new Icon(logoUrl, "192x192", PNG, PURPOSE_ANY));
    }

    /**
     * Nombre recortado a 12 caracteres para el ícono de inicio: conserva palabras completas mientras
     * quepan; si la primera palabra ya no cabe, la corta. Cuenta code points para no partir emojis.
     */
    static String shortName(String name) {
        String trimmed = name.strip().replaceAll("\\s+", " ");
        if (trimmed.codePointCount(0, trimmed.length()) <= SHORT_NAME_MAX_LENGTH) {
            return trimmed;
        }
        StringBuilder result = new StringBuilder();
        for (String word : trimmed.split(" ")) {
            String candidate = result.isEmpty() ? word : result + " " + word;
            if (candidate.codePointCount(0, candidate.length()) > SHORT_NAME_MAX_LENGTH) {
                break;
            }
            result.setLength(0);
            result.append(candidate);
        }
        if (!result.isEmpty()) {
            return result.toString();
        }
        int end = trimmed.offsetByCodePoints(0, SHORT_NAME_MAX_LENGTH);
        return trimmed.substring(0, end);
    }
}

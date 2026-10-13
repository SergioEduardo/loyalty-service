package com.axioma.loyalty.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Reglas del logo del negocio: lo que se acepta al subirlo y dónde se guarda ya normalizado. */
public final class TenantLogo {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Ancho y alto máximos de la imagen original; se revisan antes de decodificarla completa. */
    public static final int MAX_DIMENSION = 4000;
    /** El logo normalizado es un PNG cuadrado de este lado. */
    public static final int SIZE_PX = 512;
    public static final String CONTENT_TYPE = "image/png";
    /** La URL cambia con el contenido, así que el objeto nunca cambia y se puede guardar en caché un año. */
    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private TenantLogo() {
    }

    /** tenants/{tenantId}/logo-{sha256 del PNG normalizado}.png */
    public static String objectKey(UUID tenantId, byte[] normalizedPng) {
        return "tenants/" + tenantId + "/logo-" + sha256Hex(normalizedPng) + ".png";
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

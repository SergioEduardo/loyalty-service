package com.axioma.loyalty.domain.model;

/** Identidad pública de un negocio: lo único que ve alguien sin iniciar sesión. */
public record TenantIdentity(
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor) {
}

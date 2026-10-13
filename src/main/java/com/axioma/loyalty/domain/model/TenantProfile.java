package com.axioma.loyalty.domain.model;

/** Lo que el dueño ve y edita de su negocio: identidad y configuración del programa. */
public record TenantProfile(
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor,
        int pointsGoal,
        String rewardText,
        int cooldownSeconds,
        int undoWindowSeconds) {

    public static TenantProfile from(Tenant tenant) {
        return new TenantProfile(
                tenant.slug().value(),
                tenant.name(),
                tenant.description(),
                tenant.logoUrl(),
                tenant.brandColor(),
                tenant.pointsGoal(),
                tenant.rewardText(),
                tenant.cooldownSeconds(),
                tenant.undoWindowSeconds());
    }
}

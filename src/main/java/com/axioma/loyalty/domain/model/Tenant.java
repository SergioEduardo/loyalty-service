package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Negocio y la configuración de su programa de puntos. */
public record Tenant(
        UUID id,
        Slug slug,
        String name,
        String description,
        String logoUrl,
        String logoObjectKey,
        String brandColor,
        int pointsGoal,
        String rewardText,
        int cooldownSeconds,
        int undoWindowSeconds,
        boolean active) {

    public Tenant withIdentity(String name, String description, String brandColor) {
        return new Tenant(id, slug, name, description, logoUrl, logoObjectKey, brandColor, pointsGoal, rewardText,
                cooldownSeconds, undoWindowSeconds, active);
    }

    public Tenant withSettings(int pointsGoal, String rewardText, int cooldownSeconds, int undoWindowSeconds) {
        return new Tenant(id, slug, name, description, logoUrl, logoObjectKey, brandColor, pointsGoal, rewardText,
                cooldownSeconds, undoWindowSeconds, active);
    }

    public Tenant withLogo(StoredLogo logo) {
        return new Tenant(id, slug, name, description, logo.url(), logo.objectKey(), brandColor, pointsGoal,
                rewardText, cooldownSeconds, undoWindowSeconds, active);
    }

    public Tenant withoutLogo() {
        return new Tenant(id, slug, name, description, null, null, brandColor, pointsGoal, rewardText,
                cooldownSeconds, undoWindowSeconds, active);
    }
}

package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Negocio y la configuración de su programa de puntos. */
public record Tenant(
        UUID id,
        Slug slug,
        String name,
        String description,
        String logoUrl,
        String brandColor,
        int pointsGoal,
        String rewardText,
        int cooldownSeconds,
        int undoWindowSeconds,
        boolean active) {
}

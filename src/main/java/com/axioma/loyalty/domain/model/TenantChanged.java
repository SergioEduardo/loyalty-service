package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Aviso para el wallet-service: estado completo del negocio que necesita para los pases. */
public record TenantChanged(
        UUID tenantId,
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor,
        int pointsGoal,
        String rewardText) {

    public static final String EVENT_TYPE = "TenantChanged";

    public static TenantChanged from(Tenant tenant) {
        return new TenantChanged(
                tenant.id(),
                tenant.slug().value(),
                tenant.name(),
                tenant.description(),
                tenant.logoUrl(),
                tenant.brandColor(),
                tenant.pointsGoal(),
                tenant.rewardText());
    }
}

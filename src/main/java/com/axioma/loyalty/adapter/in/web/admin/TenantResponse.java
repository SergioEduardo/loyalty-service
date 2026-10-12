package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.domain.model.Tenant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor,
        int pointsGoal,
        String rewardText,
        int cooldownSeconds,
        int undoWindowSeconds,
        boolean active) {

    static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.id(),
                tenant.slug().value(),
                tenant.name(),
                tenant.description(),
                tenant.logoUrl(),
                tenant.brandColor(),
                tenant.pointsGoal(),
                tenant.rewardText(),
                tenant.cooldownSeconds(),
                tenant.undoWindowSeconds(),
                tenant.active());
    }
}

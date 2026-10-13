package com.axioma.loyalty.adapter.in.web.staff;

import com.axioma.loyalty.domain.model.TenantProfile;

public record TenantProfileResponse(
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor,
        Settings settings) {

    public record Settings(int pointsGoal, String rewardText, int cooldownSeconds, int undoWindowSeconds) {
    }

    static TenantProfileResponse from(TenantProfile profile) {
        return new TenantProfileResponse(
                profile.slug(),
                profile.name(),
                profile.description(),
                profile.logoUrl(),
                profile.brandColor(),
                new Settings(profile.pointsGoal(), profile.rewardText(), profile.cooldownSeconds(),
                        profile.undoWindowSeconds()));
    }
}

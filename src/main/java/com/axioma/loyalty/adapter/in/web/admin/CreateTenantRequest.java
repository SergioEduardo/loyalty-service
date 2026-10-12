package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.application.command.CreateTenantCommand;

public record CreateTenantRequest(
        String slug,
        String name,
        String description,
        String brandColor,
        Integer pointsGoal,
        String rewardText,
        Integer cooldownSeconds,
        Integer undoWindowSeconds) {

    CreateTenantCommand toCommand() {
        return new CreateTenantCommand(slug, name, description, brandColor, pointsGoal, rewardText,
                cooldownSeconds, undoWindowSeconds);
    }
}

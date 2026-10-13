package com.axioma.loyalty.adapter.in.web.staff;

import com.axioma.loyalty.application.command.UpdateTenantSettingsCommand;
import java.util.UUID;

/** Sin tenantId ni slug: si el cliente los manda, se ignoran. */
public record UpdateTenantSettingsRequest(
        Integer pointsGoal,
        String rewardText,
        Integer cooldownSeconds,
        Integer undoWindowSeconds) {

    UpdateTenantSettingsCommand toCommand(UUID tenantId) {
        return new UpdateTenantSettingsCommand(tenantId, pointsGoal, rewardText, cooldownSeconds, undoWindowSeconds);
    }
}

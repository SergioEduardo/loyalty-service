package com.axioma.loyalty.application.command;

import com.axioma.loyalty.application.validator.TextRules;
import java.util.UUID;

/** El negocio sale del token. Todos los valores son obligatorios: reemplazan la configuración. */
public record UpdateTenantSettingsCommand(
        UUID tenantId,
        Integer pointsGoal,
        String rewardText,
        Integer cooldownSeconds,
        Integer undoWindowSeconds) {

    public UpdateTenantSettingsCommand {
        rewardText = TextRules.clean(rewardText);
    }
}

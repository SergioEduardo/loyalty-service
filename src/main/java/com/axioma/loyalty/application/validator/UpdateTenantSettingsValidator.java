package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.UpdateTenantSettingsCommand;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Mismas reglas que el alta admin (CreateTenantValidator), pero sin valores por defecto. */
@Component
public class UpdateTenantSettingsValidator {

    public Mono<Void> validate(UpdateTenantSettingsCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private static void check(UpdateTenantSettingsCommand command) {
        TextRules.required(command.rewardText(), CreateTenantValidator.REWARD_TEXT_MAX);
        if (command.pointsGoal() == null || command.pointsGoal() <= 0
                || command.cooldownSeconds() == null || command.cooldownSeconds() < 0
                || command.undoWindowSeconds() == null || command.undoWindowSeconds() < 0) {
            throw new LoyaltyException(ErrorCode.INVALID_SETTINGS);
        }
    }
}

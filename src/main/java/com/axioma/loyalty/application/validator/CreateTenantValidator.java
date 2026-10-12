package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.CreateTenantCommand;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CreateTenantValidator {

    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 300;
    static final int REWARD_TEXT_MAX = 100;
    private static final Pattern BRAND_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public Mono<Void> validate(CreateTenantCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private static void check(CreateTenantCommand command) {
        if (!Slug.isValid(command.slug())) {
            throw new LoyaltyException(ErrorCode.INVALID_SLUG);
        }
        TextRules.required(command.name(), NAME_MAX);
        TextRules.optional(command.description(), DESCRIPTION_MAX);
        TextRules.required(command.rewardText(), REWARD_TEXT_MAX);
        if (command.brandColor() != null && !BRAND_COLOR.matcher(command.brandColor()).matches()) {
            throw new LoyaltyException(ErrorCode.INVALID_BRAND_COLOR);
        }
        if (command.pointsGoal() == null || command.pointsGoal() <= 0
                || command.cooldownSeconds() < 0
                || command.undoWindowSeconds() < 0) {
            throw new LoyaltyException(ErrorCode.INVALID_SETTINGS);
        }
    }
}

package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Identidad que edita el dueño. Más estricta que el alta admin: es lo que se ve en los pases. */
@Component
public class UpdateTenantIdentityValidator {

    static final int NAME_MIN = 2;
    static final int NAME_MAX = 60;
    static final int DESCRIPTION_MAX = 140;
    private static final Pattern BRAND_COLOR = Pattern.compile("^#[0-9a-f]{6}$");

    public Mono<Void> validate(UpdateTenantIdentityCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private static void check(UpdateTenantIdentityCommand command) {
        TextRules.required(command.name(), NAME_MAX);
        if (command.name().codePointCount(0, command.name().length()) < NAME_MIN
                || command.name().codePoints().anyMatch(Character::isISOControl)) {
            throw new LoyaltyException(ErrorCode.INVALID_TEXT);
        }
        TextRules.optional(command.description(), DESCRIPTION_MAX);
        if (command.brandColor() == null || !BRAND_COLOR.matcher(command.brandColor()).matches()) {
            throw new LoyaltyException(ErrorCode.INVALID_BRAND_COLOR);
        }
    }
}

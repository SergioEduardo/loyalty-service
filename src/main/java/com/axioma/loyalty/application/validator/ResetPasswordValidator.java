package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ResetPasswordValidator {

    private final PasswordPolicy passwordPolicy;

    public Mono<Void> validate(ResetPasswordCommand command) {
        return Mono.fromRunnable(() -> {
            if (command.tenantId() == null || command.userId() == null) {
                throw new LoyaltyException(ErrorCode.INVALID_REQUEST);
            }
            passwordPolicy.check(command.newPassword());
        });
    }
}

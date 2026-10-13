package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.ActivateAccountCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** La contraseña sigue la misma política que el alta y el cambio de contraseña (PasswordPolicy). */
@Component
@RequiredArgsConstructor
public class ActivateAccountValidator {

    private final PasswordPolicy passwordPolicy;

    public Mono<Void> validate(ActivateAccountCommand command) {
        return Mono.fromRunnable(() -> passwordPolicy.check(command.password()));
    }
}

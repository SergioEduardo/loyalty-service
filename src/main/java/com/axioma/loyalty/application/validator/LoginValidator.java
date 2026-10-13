package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.LoginCommand;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Solo revisa la forma. Campos ausentes: INVALID_REQUEST. Valores que ninguna cuenta puede
 * tener (usuario o contraseña demasiado largos): INVALID_CREDENTIALS, sin decir cuál.
 */
@Component
public class LoginValidator {

    static final int USERNAME_MAX = 60;

    public Mono<Void> validate(LoginCommand command) {
        return Mono.fromRunnable(() -> {
            if (isBlank(command.slug()) || isBlank(command.username()) || command.password() == null
                    || command.password().isEmpty()) {
                throw new LoyaltyException(ErrorCode.INVALID_REQUEST);
            }
            if (command.username().length() > USERNAME_MAX
                    || command.password().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
                throw new LoyaltyException(ErrorCode.INVALID_CREDENTIALS);
            }
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

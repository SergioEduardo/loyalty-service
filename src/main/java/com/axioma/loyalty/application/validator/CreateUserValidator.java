package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CreateUserValidator {

    static final int NAME_MAX = 80;
    /** 3 a 60 caracteres: letras, números y . _ @ + - (un nombre o un correo); empieza y termina en letra o número. */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._@+-]{1,58}[a-z0-9]$");

    private final PasswordPolicy passwordPolicy;

    public Mono<Void> validate(CreateUserCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private void check(CreateUserCommand command) {
        if (command.tenantId() == null
                || command.username() == null
                || !USERNAME.matcher(command.username()).matches()
                || Arrays.stream(UserRole.values()).noneMatch(r -> r.name().equals(command.role()))) {
            throw new LoyaltyException(ErrorCode.INVALID_USER);
        }
        try {
            TextRules.required(command.name(), NAME_MAX);
        } catch (LoyaltyException e) {
            throw new LoyaltyException(ErrorCode.INVALID_USER);
        }
        passwordPolicy.check(command.password());
    }
}

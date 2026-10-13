package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Sin contraseña (null) la cuenta queda pendiente de activación; si viene, debe cumplir la política.
 * Cada rechazo INVALID_USER se registra en WARN con el campo y el motivo, nunca con el valor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateUserValidator {

    static final int NAME_MAX = 80;
    /** 3 a 60 caracteres: letras, números y . _ @ + - (un nombre o un correo); empieza y termina en letra o número. */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._@+-]{1,58}[a-z0-9]$");
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 60;

    private final PasswordPolicy passwordPolicy;

    public Mono<Void> validate(CreateUserCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private void check(CreateUserCommand command) {
        if (command.tenantId() == null) {
            reject("tenantId", "missing");
        }
        if (command.username() == null) {
            reject("username", "missing");
        }
        if (!USERNAME.matcher(command.username()).matches()) {
            reject("username", usernameProblem(command.username()));
        }
        if (command.role() == null) {
            reject("role", "missing");
        }
        if (Arrays.stream(UserRole.values()).noneMatch(r -> r.name().equals(command.role()))) {
            reject("role", "unknown");
        }
        try {
            TextRules.required(command.name(), NAME_MAX);
        } catch (LoyaltyException e) {
            reject("name", command.name() == null ? "missing" : "too_long_or_control_chars");
        }
        if (command.password() != null) {
            passwordPolicy.check(command.password());
        }
    }

    /** Motivo sin revelar el valor. */
    private static String usernameProblem(String username) {
        int length = username.codePointCount(0, username.length());
        if (length < USERNAME_MIN) {
            return "too_short";
        }
        if (length > USERNAME_MAX) {
            return "too_long";
        }
        if (!username.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "._@+-".indexOf(c) >= 0)) {
            return "invalid_chars";
        }
        return "must_start_and_end_with_letter_or_digit";
    }

    private static void reject(String field, String reason) {
        log.warn("Invalid user field={} reason={}", field, reason);
        throw new LoyaltyException(ErrorCode.INVALID_USER);
    }
}

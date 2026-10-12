package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.TENANT_ID;
import static com.axioma.loyalty.application.AdminFixtures.USER_ID;
import static com.axioma.loyalty.application.AdminFixtures.hasCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.application.validator.CreateUserValidator;
import com.axioma.loyalty.application.validator.PasswordPolicy;
import com.axioma.loyalty.application.validator.ResetPasswordValidator;
import com.axioma.loyalty.config.SecurityProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class CreateUserValidatorTest {

    private final PasswordPolicy policy = new PasswordPolicy(new SecurityProperties(8, 4));
    private final CreateUserValidator validator = new CreateUserValidator(policy);
    private final ResetPasswordValidator resetValidator = new ResetPasswordValidator(policy);

    @Test
    void normalizesUsernameAndDefaultsName() {
        var command = new CreateUserCommand(TENANT_ID, null, "  Ana.Lopez@Cafe.MX ", "secreto123", "cashier");

        StepVerifier.create(validator.validate(command)).verifyComplete();
        assertThat(command.username()).isEqualTo("ana.lopez@cafe.mx");
        assertThat(command.name()).isEqualTo("ana.lopez@cafe.mx");
        assertThat(command.role()).isEqualTo("CASHIER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "ana lopez", "-ana", "ana-", "aña"})
    void rejectsInvalidUsernames(String username) {
        StepVerifier.create(validator.validate(new CreateUserCommand(TENANT_ID, null, username, "secreto123", "OWNER")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_USER)).verify();
    }

    @Test
    void rejectsUnknownRole() {
        StepVerifier.create(validator.validate(new CreateUserCommand(TENANT_ID, null, "ana", "secreto123", "ADMIN")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_USER)).verify();
    }

    @Test
    void rejectsShortAndOver72BytePasswords() {
        StepVerifier.create(validator.validate(new CreateUserCommand(TENANT_ID, null, "ana", "corta", "OWNER")))
                .expectErrorSatisfies(hasCode(ErrorCode.WEAK_PASSWORD)).verify();
        // 37 caracteres de 2 bytes = 74 bytes
        StepVerifier.create(resetValidator.validate(new ResetPasswordCommand(TENANT_ID, USER_ID, "ñ".repeat(37))))
                .expectErrorSatisfies(hasCode(ErrorCode.WEAK_PASSWORD)).verify();
        StepVerifier.create(resetValidator.validate(new ResetPasswordCommand(TENANT_ID, USER_ID, "a".repeat(72))))
                .verifyComplete();
    }

    @Test
    void commandsNeverPrintThePassword() {
        assertThat(new CreateUserCommand(TENANT_ID, null, "ana", "SuperSecreta1", "OWNER").toString())
                .doesNotContain("SuperSecreta1");
        assertThat(new ResetPasswordCommand(TENANT_ID, USER_ID, "SuperSecreta1").toString())
                .doesNotContain("SuperSecreta1");
    }
}

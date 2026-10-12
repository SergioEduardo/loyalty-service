package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.hasCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.application.command.CreateTenantCommand;
import com.axioma.loyalty.application.validator.CreateTenantValidator;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CreateTenantValidatorTest {

    private final CreateTenantValidator validator = new CreateTenantValidator();

    private static CreateTenantCommand command(String slug, String name, String color, Integer goal,
                                               String reward, Integer cooldown) {
        return new CreateTenantCommand(slug, name, "Bienvenido", color, goal, reward, cooldown, null);
    }

    @Test
    void acceptsValidTenantAndAppliesDefaults() {
        var command = command("mi-cafe", "  Mi Café ", "#A1b2C3", 10, "Café gratis", null);

        StepVerifier.create(validator.validate(command)).verifyComplete();
        assertThat(command.name()).isEqualTo("Mi Café");
        assertThat(command.cooldownSeconds()).isZero();
        assertThat(command.undoWindowSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsInvalidSlug() {
        StepVerifier.create(validator.validate(command("Mi Cafe", "Mi Café", null, 10, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SLUG)).verify();
    }

    @Test
    void rejectsMissingOrLongTexts() {
        StepVerifier.create(validator.validate(command("mi-cafe", "   ", null, 10, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
        StepVerifier.create(validator.validate(command("mi-cafe", "a".repeat(101), null, 10, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
        StepVerifier.create(validator.validate(command("mi-cafe", "Mi\u0000Café", null, 10, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
    }

    @Test
    void rejectsBadBrandColor() {
        StepVerifier.create(validator.validate(command("mi-cafe", "Mi Café", "red", 10, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_BRAND_COLOR)).verify();
    }

    @Test
    void rejectsBadSettings() {
        StepVerifier.create(validator.validate(command("mi-cafe", "Mi Café", null, 0, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SETTINGS)).verify();
        StepVerifier.create(validator.validate(command("mi-cafe", "Mi Café", null, null, "x", 0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SETTINGS)).verify();
        StepVerifier.create(validator.validate(command("mi-cafe", "Mi Café", null, 10, "x", -1)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SETTINGS)).verify();
    }
}

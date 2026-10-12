package com.axioma.loyalty.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.application.validator.GetTenantIdentityValidator;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class GetTenantIdentityValidatorTest {

    private final GetTenantIdentityValidator validator = new GetTenantIdentityValidator();

    @ParameterizedTest
    @ValueSource(strings = {"mi-cafe", "cafe", "a", "tienda-24-horas", "x1"})
    void acceptsValidSlugs(String slug) {
        StepVerifier.create(validator.validate(new GetTenantIdentityQuery(slug)))
                .verifyComplete();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Mi-Cafe", "mi cafe", "-cafe", "cafe-", "mi--cafe", "café", "mi_cafe",
            "../etc", "a;b"})
    void rejectsInvalidSlugs(String slug) {
        StepVerifier.create(validator.validate(new GetTenantIdentityQuery(slug)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LoyaltyException.class);
                    assertThat(((LoyaltyException) error).getCode()).isEqualTo(ErrorCode.INVALID_SLUG);
                })
                .verify();
    }

    @org.junit.jupiter.api.Test
    void rejectsSlugLongerThan50() {
        String tooLong = "a".repeat(51);
        StepVerifier.create(validator.validate(new GetTenantIdentityQuery(tooLong)))
                .expectError(LoyaltyException.class)
                .verify();
    }
}

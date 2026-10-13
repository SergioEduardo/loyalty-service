package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.ErrorAssertions.hasCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.application.command.EnrollCardCommand;
import com.axioma.loyalty.application.command.RequestPhoneVerificationCommand;
import com.axioma.loyalty.application.validator.EnrollCardValidator;
import com.axioma.loyalty.application.validator.PhoneRules;
import com.axioma.loyalty.application.validator.RequestPhoneVerificationValidator;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class EnrollCardValidatorTest {

    private static final String ID = UUID.randomUUID().toString();
    private final EnrollCardValidator validator = new EnrollCardValidator();

    @ParameterizedTest
    @ValueSource(strings = {"5512345678", "55 1234 5678", "(55) 1234-5678", "+52 55 1234 5678", "+525512345678",
            "+52 1 55 1234 5678"})
    void normalizesMexicanNumbers(String raw) {
        assertThat(PhoneRules.normalize(raw).value()).isEqualTo("+525512345678");
    }

    @Test
    void keepsForeignNumbers() {
        assertThat(PhoneRules.normalize("+1 650 253 0000").value()).isEqualTo("+16502530000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "123", "abc", "+52 55", "+999 1234 5678", "55123456789012345678901234567890123456789"})
    void rejectsInvalidPhones(String raw) {
        StepVerifier.create(new RequestPhoneVerificationValidator()
                        .validate(new RequestPhoneVerificationCommand("mi-cafe", raw, null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_PHONE)).verify();
    }

    @Test
    void validEnrollmentIsNormalized() {
        StepVerifier.create(validator.validate(new EnrollCardCommand(" Mi-Cafe ", "55 1234 5678", "  Ana López ",
                        ID, " 012345 ", null)))
                .assertNext(request -> {
                    assertThat(request.slug()).isEqualTo("mi-cafe");
                    assertThat(request.phone().value()).isEqualTo("+525512345678");
                    assertThat(request.holderName()).isEqualTo("Ana López");
                    assertThat(request.code()).isEqualTo("012345");
                    assertThat(request.toString()).doesNotContain("Ana").doesNotContain("5512345678")
                            .doesNotContain("012345");
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "<script>", "Ana\tLópez", "Ana\u0007", "‮ana", "Ana⁦x"})
    void rejectsNames(String name) {
        StepVerifier.create(validator.validate(new EnrollCardCommand("mi-cafe", "5512345678", name, ID, "123456", null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_NAME)).verify();
    }

    @Test
    void nameLengthCountsCodePoints() {
        String eighty = "😀".repeat(80);
        StepVerifier.create(validator.validate(new EnrollCardCommand("mi-cafe", "5512345678", eighty, ID, "123456", null)))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(validator.validate(new EnrollCardCommand("mi-cafe", "5512345678", eighty + "a", ID, "123456", null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_NAME)).verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12a456", "١٢٣٤٥٦", ""})
    void rejectsCodes(String code) {
        StepVerifier.create(validator.validate(new EnrollCardCommand("mi-cafe", "5512345678", "Ana", ID, code, null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CODE)).verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-es-uuid", "123"})
    void malformedVerificationIdIsInvalidCode(String id) {
        StepVerifier.create(validator.validate(new EnrollCardCommand("mi-cafe", "5512345678", "Ana", id, "123456", null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_CODE)).verify();
    }

    @Test
    void commandsHideSensitiveFields() {
        assertThat(new EnrollCardCommand("mi-cafe", "5512345678", "Ana", ID, "123456", "10.0.0.1").toString())
                .doesNotContain("5512345678").doesNotContain("Ana").doesNotContain("123456")
                .doesNotContain(ID).doesNotContain("10.0.0.1");
        assertThat(new RequestPhoneVerificationCommand("mi-cafe", "5512345678", "10.0.0.1").toString())
                .doesNotContain("5512345678").doesNotContain("10.0.0.1");
    }
}

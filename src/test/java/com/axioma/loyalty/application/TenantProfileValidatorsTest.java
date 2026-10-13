package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.ErrorAssertions.hasCode;

import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import com.axioma.loyalty.application.command.UpdateTenantSettingsCommand;
import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.application.validator.UpdateTenantIdentityValidator;
import com.axioma.loyalty.application.validator.UpdateTenantSettingsValidator;
import com.axioma.loyalty.application.validator.UploadTenantLogoValidator;
import com.axioma.loyalty.domain.model.ImageFormat;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.support.TestImages;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class TenantProfileValidatorsTest {

    private static final UUID TENANT = AdminFixtures.TENANT_ID;

    private final UpdateTenantIdentityValidator identity = new UpdateTenantIdentityValidator();
    private final UpdateTenantSettingsValidator settings = new UpdateTenantSettingsValidator();
    private final UploadTenantLogoValidator logo = new UploadTenantLogoValidator();

    // --- identidad ---

    @Test
    void validIdentityIsTrimmedAndColorLowercased() {
        var command = new UpdateTenantIdentityCommand(TENANT, "  Mi Café  ", "  Hola  ", " #AABBCC ");
        Assertions.assertThat(command.name()).isEqualTo("Mi Café");
        Assertions.assertThat(command.description()).isEqualTo("Hola");
        Assertions.assertThat(command.brandColor()).isEqualTo("#aabbcc");
        StepVerifier.create(identity.validate(command)).verifyComplete();
    }

    @Test
    void nameLimitsAreTwoToSixtyAfterTrim() {
        StepVerifier.create(identity.validate(identityWithName("  ab  "))).verifyComplete();
        StepVerifier.create(identity.validate(identityWithName("é".repeat(60)))).verifyComplete();
        for (String bad : new String[] {null, "", "   ", " a ", "x".repeat(61), "Mi\nCafé", "Mi\tCafé"}) {
            StepVerifier.create(identity.validate(identityWithName(bad)))
                    .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
        }
    }

    @Test
    void descriptionIsOptionalUpTo140() {
        StepVerifier.create(identity.validate(new UpdateTenantIdentityCommand(TENANT, "Mi Café", null, "#112233")))
                .verifyComplete();
        StepVerifier.create(identity.validate(new UpdateTenantIdentityCommand(TENANT, "Mi Café", "   ", "#112233")))
                .verifyComplete();
        StepVerifier.create(identity.validate(
                        new UpdateTenantIdentityCommand(TENANT, "Mi Café", "d".repeat(140), "#112233")))
                .verifyComplete();
        StepVerifier.create(identity.validate(
                        new UpdateTenantIdentityCommand(TENANT, "Mi Café", "d".repeat(141), "#112233")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "112233", "#12345", "#1234567", "#GGGGGG", "red", "#12 345"})
    void brandColorMustBeHexRgb(String color) {
        StepVerifier.create(identity.validate(new UpdateTenantIdentityCommand(TENANT, "Mi Café", null, color)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_BRAND_COLOR)).verify();
    }

    @Test
    void brandColorIsRequired() {
        StepVerifier.create(identity.validate(new UpdateTenantIdentityCommand(TENANT, "Mi Café", null, null)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_BRAND_COLOR)).verify();
    }

    // --- configuración ---

    @Test
    void validSettings() {
        StepVerifier.create(settings.validate(new UpdateTenantSettingsCommand(TENANT, 8, " Postre ", 0, 0)))
                .verifyComplete();
    }

    @Test
    void invalidSettings() {
        for (var command : new UpdateTenantSettingsCommand[] {
                new UpdateTenantSettingsCommand(TENANT, 0, "Postre", 0, 300),
                new UpdateTenantSettingsCommand(TENANT, null, "Postre", 0, 300),
                new UpdateTenantSettingsCommand(TENANT, 10, "Postre", -1, 300),
                new UpdateTenantSettingsCommand(TENANT, 10, "Postre", null, 300),
                new UpdateTenantSettingsCommand(TENANT, 10, "Postre", 0, -5),
                new UpdateTenantSettingsCommand(TENANT, 10, "Postre", 0, null)}) {
            StepVerifier.create(settings.validate(command))
                    .expectErrorSatisfies(hasCode(ErrorCode.INVALID_SETTINGS)).verify();
        }
        StepVerifier.create(settings.validate(new UpdateTenantSettingsCommand(TENANT, 10, " ", 0, 300)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT)).verify();
    }

    // --- logo ---

    @Test
    void acceptsPngJpegWebpByContent() {
        StepVerifier.create(logo.validate(upload(TestImages.png(10, 10)))).expectNext(ImageFormat.PNG).verifyComplete();
        StepVerifier.create(logo.validate(upload(TestImages.jpeg(10, 10)))).expectNext(ImageFormat.JPEG)
                .verifyComplete();
        StepVerifier.create(logo.validate(upload(TestImages.webp(false)))).expectNext(ImageFormat.WEBP)
                .verifyComplete();
    }

    @Test
    void rejectsSvgAndNonImages() {
        StepVerifier.create(logo.validate(upload(TestImages.svg())))
                .expectErrorSatisfies(hasCode(ErrorCode.UNSUPPORTED_IMAGE_FORMAT)).verify();
        // Lo que sea que diga el nombre o el Content-Type, el contenido no es una imagen.
        StepVerifier.create(logo.validate(upload("esto no es un png".getBytes(StandardCharsets.UTF_8))))
                .expectErrorSatisfies(hasCode(ErrorCode.UNSUPPORTED_IMAGE_FORMAT)).verify();
    }

    @Test
    void rejectsEmptyAndOversized() {
        StepVerifier.create(logo.validate(upload(new byte[0])))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_IMAGE)).verify();
        byte[] big = Arrays.copyOf(TestImages.png(10, 10), TenantLogo.MAX_BYTES + 1);
        StepVerifier.create(logo.validate(upload(big)))
                .expectErrorSatisfies(hasCode(ErrorCode.LOGO_TOO_LARGE)).verify();
        byte[] exact = Arrays.copyOf(TestImages.png(10, 10), TenantLogo.MAX_BYTES);
        StepVerifier.create(logo.validate(upload(exact))).expectNext(ImageFormat.PNG).verifyComplete();
    }

    @Test
    void uploadCommandToStringOmitsContent() {
        Assertions.assertThat(upload(new byte[] {1, 2, 3}).toString())
                .isEqualTo("UploadTenantLogoCommand[tenantId=" + TENANT + ", bytes=3]");
    }

    private static UpdateTenantIdentityCommand identityWithName(String name) {
        return new UpdateTenantIdentityCommand(TENANT, name, null, "#112233");
    }

    private static UploadTenantLogoCommand upload(byte[] content) {
        return new UploadTenantLogoCommand(TENANT, content);
    }
}

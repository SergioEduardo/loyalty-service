package com.axioma.loyalty.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.adapter.out.verification.DisabledPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.LogPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.WhatsAppPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.wallet.DisabledWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.HttpWalletPassIssuer;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

/** Selección de adaptadores por configuración y la regla de seguridad del modo "log". */
class EnrollConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class))
            .withUserConfiguration(EnrollConfig.class);

    @Test
    void disabledByDefaultAnswersUnavailable() {
        runner.run(context -> {
            var sender = context.getBean(PhoneVerificationSenderPort.class);
            assertThat(sender).isInstanceOf(DisabledPhoneVerificationSender.class);
            StepVerifier.create(sender.ensureReady())
                    .expectErrorSatisfies(e -> assertThat(((LoyaltyException) e).getCode())
                            .isEqualTo(ErrorCode.VERIFICATION_UNAVAILABLE))
                    .verify();
        });
    }

    @Test
    void logModeStartsOnlyInDev() {
        runner.withPropertyValues("app.enroll.verification-sender=log", "app.enroll.app-env=dev")
                .run(context -> assertThat(context.getBean(PhoneVerificationSenderPort.class))
                        .isInstanceOf(LogPhoneVerificationSender.class));
        runner.withPropertyValues("app.enroll.verification-sender=LOG", "app.enroll.app-env=Dev")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void logModeRefusesToStartOutsideDev() {
        for (String env : new String[]{"prod", "staging", "", "development"}) {
            runner.withPropertyValues("app.enroll.verification-sender=log", "app.enroll.app-env=" + env)
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().rootCause().hasMessageContaining("solo se permite con APP_ENV=dev"));
        }
        runner.withPropertyValues("app.enroll.verification-sender=log")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whatsappIsNotImplementedYet() {
        runner.withPropertyValues("app.enroll.verification-sender=whatsapp").run(context -> {
            var sender = context.getBean(PhoneVerificationSenderPort.class);
            assertThat(sender).isInstanceOf(WhatsAppPhoneVerificationSender.class);
            StepVerifier.create(sender.ensureReady()).expectError(LoyaltyException.class).verify();
        });
    }

    @Test
    void unknownSenderRefusesToStart() {
        runner.withPropertyValues("app.enroll.verification-sender=sms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void walletIssuerDependsOnTheUrl() {
        runner.run(context -> assertThat(context.getBean(WalletPassIssuerPort.class))
                .isInstanceOf(DisabledWalletPassIssuer.class));
        runner.withPropertyValues("app.wallet.service-url=https://wallet.test/")
                .run(context -> {
                    assertThat(context.getBean(WalletPassIssuerPort.class)).isInstanceOf(HttpWalletPassIssuer.class);
                    var properties = context.getBean(WalletProperties.class);
                    assertThat(properties.serviceUrl()).isEqualTo("https://wallet.test");
                    assertThat(properties.audience()).isEqualTo("https://wallet.test");
                    assertThat(properties.issueTimeoutMs()).isEqualTo(5000L);
                });
    }

    @Test
    void emptyNumbersTakeDefaultsAndToStringHidesTheSalt() {
        runner.withPropertyValues("app.enroll.phone-limit-per-hour=", "app.enroll.max-attempts=",
                        "app.enroll.otp-hash-salt=una-sal-secreta-de-mas-de-32-bytes-xx")
                .run(context -> {
                    var properties = context.getBean(EnrollProperties.class);
                    assertThat(properties.phoneLimitPerHour()).isEqualTo(3);
                    assertThat(properties.ipLimitPerHour()).isEqualTo(10);
                    assertThat(properties.tenantLimitPerHour()).isEqualTo(60);
                    assertThat(properties.codeTtlMinutes()).isEqualTo(10);
                    assertThat(properties.maxAttempts()).isEqualTo(5);
                    assertThat(properties.saltConfigured()).isTrue();
                    assertThat(properties.toString()).doesNotContain("una-sal-secreta");
                });
    }
}

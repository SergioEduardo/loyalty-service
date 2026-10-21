package com.axioma.loyalty.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.adapter.out.verification.ChannelRoutingPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.DisabledPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.LogPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.wallet.DisabledWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.HttpWalletPassIssuer;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationChannelPolicy;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

/**
 * Selección de adaptadores y canales por configuración, fail fast (canal desconocido o no habilitado,
 * credenciales faltantes) y la regla de seguridad del modo "log".
 */
class EnrollConfigTest {

    private static final String WHATSAPP_TOKEN = "EAAG-token-de-prueba";
    private static final String TWILIO_SID = "AC" + "0123456789abcdef0123456789abcdef";
    private static final String TWILIO_TOKEN = "twilio-token-de-prueba";

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
    void logModeReplacesEveryEnabledChannelWithoutCredentials() {
        runner.withPropertyValues("app.enroll.verification-sender=log", "app.enroll.app-env=dev").run(context -> {
            assertThat(context.getBean(PhoneVerificationSenderPort.class)).isInstanceOf(LogPhoneVerificationSender.class);
            var channels = context.getBean(VerificationChannelPolicy.class);
            assertThat(channels.enabled()).containsExactly(VerificationChannel.WHATSAPP, VerificationChannel.SMS);
            assertThat(channels.defaultChannel()).isEqualTo(VerificationChannel.WHATSAPP);
        });
        runner.withPropertyValues("app.enroll.verification-sender=log", "app.enroll.app-env=dev",
                        "app.enroll.verification-enabled-channels=sms")
                .run(context -> {
                    var channels = context.getBean(VerificationChannelPolicy.class);
                    assertThat(channels.enabled()).containsExactly(VerificationChannel.SMS);
                    assertThat(channels.defaultChannel()).isEqualTo(VerificationChannel.SMS);
                });
    }

    @Test
    void logModeRefusesToStartInProdEvenWithCredentials() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=log", "app.enroll.app-env=prod",
                        "app.enroll.verification-enabled-channels=whatsapp,sms"))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("solo se permite con APP_ENV=dev"));
    }

    @Test
    void whatsappWithCredentialsIsTheDefaultAndOnlyChannel() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=whatsapp")).run(context -> {
            assertThat(context.getBean(PhoneVerificationSenderPort.class))
                    .isInstanceOf(ChannelRoutingPhoneVerificationSender.class);
            var channels = context.getBean(VerificationChannelPolicy.class);
            assertThat(channels.defaultChannel()).isEqualTo(VerificationChannel.WHATSAPP);
            assertThat(channels.enabled()).containsExactly(VerificationChannel.WHATSAPP);
            StepVerifier.create(context.getBean(PhoneVerificationSenderPort.class).ensureReady()).verifyComplete();
            var whatsApp = context.getBean(WhatsAppProperties.class);
            assertThat(whatsApp.templateName()).isEqualTo("yiupi_codigo_verificacion");
            assertThat(whatsApp.templateLanguage()).isEqualTo("es_MX");
            assertThat(whatsApp.graphApiVersion()).isEqualTo("v26.0");
            assertThat(whatsApp.timeoutMs()).isEqualTo(5000L);
            assertThat(whatsApp.apiBaseUrl()).isEqualTo("https://graph.facebook.com");
            assertThat(context.getBean(TwilioProperties.class).apiBaseUrl()).isEqualTo("https://api.twilio.com");
        });
    }

    @Test
    void smsDefaultWithBothChannelsKeepsTheConfiguredOrder() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=SMS",
                        "app.enroll.verification-enabled-channels= whatsapp , SMS ,sms"))
                .run(context -> {
                    var channels = context.getBean(VerificationChannelPolicy.class);
                    assertThat(channels.defaultChannel()).isEqualTo(VerificationChannel.SMS);
                    assertThat(channels.enabled()).containsExactly(VerificationChannel.WHATSAPP, VerificationChannel.SMS);
                });
    }

    @Test
    void enabledChannelWithoutCredentialsRefusesToStart() {
        runner.withPropertyValues("app.enroll.verification-sender=whatsapp",
                        "app.verification.whatsapp.phone-number-id=106540352242922")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("whatsapp está habilitado")
                        .hasMessageContaining("WHATSAPP_ACCESS_TOKEN")
                        .hasMessageNotContaining("WHATSAPP_PHONE_NUMBER_ID"));
        runner.withPropertyValues("app.enroll.verification-sender=whatsapp",
                        "app.enroll.verification-enabled-channels=whatsapp,sms",
                        "app.verification.whatsapp.access-token=" + WHATSAPP_TOKEN,
                        "app.verification.whatsapp.phone-number-id=106540352242922",
                        "app.verification.twilio.account-sid=" + TWILIO_SID,
                        "app.verification.twilio.auth-token=" + TWILIO_TOKEN)
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("sms está habilitado")
                        .hasMessageContaining("TWILIO_MESSAGING_SERVICE_SID o TWILIO_FROM")
                        .hasMessageNotContaining(TWILIO_TOKEN).hasMessageNotContaining(WHATSAPP_TOKEN));
        runner.withPropertyValues("app.enroll.verification-sender=sms")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN"));
    }

    @Test
    void credentialsOfDisabledChannelsAreNotRequired() {
        runner.withPropertyValues("app.enroll.verification-sender=sms",
                        "app.verification.twilio.account-sid=" + TWILIO_SID,
                        "app.verification.twilio.auth-token=" + TWILIO_TOKEN,
                        "app.verification.twilio.from=+15005550006")
                .run(context -> assertThat(context.getBean(VerificationChannelPolicy.class).enabled())
                        .containsExactly(VerificationChannel.SMS));
    }

    @Test
    void defaultChannelMustBeEnabled() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=whatsapp",
                        "app.enroll.verification-enabled-channels=sms"))
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("VERIFICATION_SENDER=whatsapp debe estar en VERIFICATION_ENABLED_CHANNELS"));
    }

    @Test
    void unknownEnabledChannelRefusesToStart() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=whatsapp",
                        "app.enroll.verification-enabled-channels=whatsapp,telegram"))
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("VERIFICATION_ENABLED_CHANNELS solo admite whatsapp y sms"));
    }

    @Test
    void malformedProviderSettingsRefuseToStart() {
        for (String bad : new String[]{"app.verification.twilio.account-sid=XX123",
                "app.verification.twilio.messaging-service-sid=PN0123",
                "app.verification.twilio.from=5512345678",
                "app.verification.whatsapp.graph-api-version=26",
                "app.verification.whatsapp.template-language=es-MX",
                "app.verification.whatsapp.phone-number-id=abc",
                "app.verification.whatsapp.timeout-ms=0",
                "app.verification.twilio.timeout-ms=60000"}) {
            runner.withPropertyValues(bad).run(context -> assertThat(context).as(bad).hasFailed());
        }
    }

    @Test
    void disabledIgnoresTheEnabledChannels() {
        runner.withPropertyValues("app.enroll.verification-enabled-channels=whatsapp,sms").run(context -> {
            assertThat(context.getBean(PhoneVerificationSenderPort.class))
                    .isInstanceOf(DisabledPhoneVerificationSender.class);
            assertThat(context.getBean(VerificationChannelPolicy.class).enabled()).isEmpty();
        });
    }

    @Test
    void unknownSenderRefusesToStart() {
        runner.withPropertyValues("app.enroll.verification-sender=telegram")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("VERIFICATION_SENDER debe ser disabled, log, whatsapp o sms"));
    }

    @Test
    void providerPropertiesToStringHidesTheTokens() {
        runner.withPropertyValues(withAllCredentials("app.enroll.verification-sender=whatsapp")).run(context -> {
            assertThat(context.getBean(WhatsAppProperties.class).toString()).doesNotContain(WHATSAPP_TOKEN);
            assertThat(context.getBean(TwilioProperties.class).toString()).doesNotContain(TWILIO_TOKEN);
        });
    }

    private static String[] withAllCredentials(String... properties) {
        var all = new ArrayList<>(List.of(properties));
        all.add("app.verification.whatsapp.access-token=" + WHATSAPP_TOKEN);
        all.add("app.verification.whatsapp.phone-number-id=106540352242922");
        all.add("app.verification.twilio.account-sid=" + TWILIO_SID);
        all.add("app.verification.twilio.auth-token=" + TWILIO_TOKEN);
        all.add("app.verification.twilio.messaging-service-sid=MG" + "fedcba9876543210fedcba9876543210");
        return all.toArray(String[]::new);
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
                    assertThat(properties.issueTimeoutMs()).isEqualTo(20000L);
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

package com.axioma.loyalty.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationChannelPolicyTest {

    private final VerificationChannelPolicy both = new VerificationChannelPolicy(VerificationChannel.WHATSAPP,
            List.of(VerificationChannel.WHATSAPP, VerificationChannel.SMS));
    private final VerificationChannelPolicy onlyWhatsApp = new VerificationChannelPolicy(VerificationChannel.WHATSAPP,
            List.of(VerificationChannel.WHATSAPP));

    @Test
    void missingChannelUsesTheDefault() {
        assertThat(both.resolve(null)).isEqualTo(VerificationChannel.WHATSAPP);
        assertThat(both.resolve("")).isEqualTo(VerificationChannel.WHATSAPP);
        assertThat(both.resolve("  ")).isEqualTo(VerificationChannel.WHATSAPP);
    }

    @Test
    void enabledChannelIgnoringCaseAndSpaces() {
        assertThat(both.resolve("sms")).isEqualTo(VerificationChannel.SMS);
        assertThat(both.resolve(" SMS ")).isEqualTo(VerificationChannel.SMS);
        assertThat(both.resolve("WhatsApp")).isEqualTo(VerificationChannel.WHATSAPP);
    }

    @Test
    void unknownOrNotEnabledChannelIsTheSameError() {
        for (String channel : new String[]{"sms", "telegram", "email", "log", "whatsapp,sms"}) {
            assertThatThrownBy(() -> onlyWhatsApp.resolve(channel))
                    .isInstanceOfSatisfying(LoyaltyException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CHANNEL_NOT_AVAILABLE))
                    .hasMessage("Ese medio de envío no está disponible.");
        }
    }

    @Test
    void noChannelsResolvesNothing() {
        var none = VerificationChannelPolicy.none();
        assertThat(none.enabled()).isEmpty();
        assertThatThrownBy(() -> none.resolve(null)).isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> none.resolve("whatsapp")).isInstanceOf(LoyaltyException.class);
    }

    @Test
    void defaultMustBeEnabled() {
        assertThatThrownBy(() -> new VerificationChannelPolicy(VerificationChannel.SMS, List.of(VerificationChannel.WHATSAPP)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationChannelPolicy(null, List.of(VerificationChannel.WHATSAPP)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

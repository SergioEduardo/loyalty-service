package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.domain.model.SentVerification;
import com.axioma.loyalty.domain.model.VerificationChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** channel: el canal usado; availableChannels: los habilitados, en el orden configurado. */
public record PhoneVerificationResponse(UUID verificationId, Instant expiresAt, String channel,
                                        List<String> availableChannels) {

    static PhoneVerificationResponse from(SentVerification sent) {
        return new PhoneVerificationResponse(sent.id(), sent.expiresAt(), sent.channel().value(),
                sent.availableChannels().stream().map(VerificationChannel::value).toList());
    }
}

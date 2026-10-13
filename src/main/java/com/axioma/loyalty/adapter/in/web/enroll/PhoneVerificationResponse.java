package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.domain.model.IssuedVerification;
import java.time.Instant;
import java.util.UUID;

public record PhoneVerificationResponse(UUID verificationId, Instant expiresAt) {

    static PhoneVerificationResponse from(IssuedVerification issued) {
        return new PhoneVerificationResponse(issued.id(), issued.expiresAt());
    }
}

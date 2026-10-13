package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import reactor.core.publisher.Mono;

/** VERIFICATION_SENDER=disabled (por defecto): no envía; pedir un código responde VERIFICATION_UNAVAILABLE. */
public class DisabledPhoneVerificationSender implements PhoneVerificationSenderPort {

    @Override
    public Mono<Void> ensureReady() {
        return Mono.error(new LoyaltyException(ErrorCode.VERIFICATION_UNAVAILABLE));
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        return ensureReady();
    }
}

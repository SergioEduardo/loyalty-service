package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER=whatsapp. Pendiente: el envío real con la plantilla de autenticación aprobada
 * por Meta (token en Secret Manager: whatsapp-api-token). Mientras tanto responde VERIFICATION_UNAVAILABLE.
 */
public class WhatsAppPhoneVerificationSender implements PhoneVerificationSenderPort {

    @Override
    public Mono<Void> ensureReady() {
        return Mono.error(new LoyaltyException(ErrorCode.VERIFICATION_UNAVAILABLE));
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        return ensureReady();
    }
}

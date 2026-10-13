package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER=log, solo con APP_ENV=dev (EnrollConfig no arranca en otro caso): en vez de
 * enviar, escribe el código en un log INFO para probar el alta. Nunca el teléfono ni el nombre.
 */
@Slf4j
public class LogPhoneVerificationSender implements PhoneVerificationSenderPort {

    @Override
    public Mono<Void> ensureReady() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        return Mono.fromRunnable(() -> log.info("Verification code (VERIFICATION_SENDER=log) tenantId={} verificationId={} code={}",
                message.tenantId(), message.verificationId(), message.code()));
    }
}

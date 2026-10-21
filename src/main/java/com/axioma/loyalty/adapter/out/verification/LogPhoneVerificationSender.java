package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER=log, solo con APP_ENV=dev (EnrollConfig no arranca en otro caso): reemplaza a todos
 * los canales habilitados y, en vez de enviar, escribe el canal pedido y el código en un log INFO para
 * probar el alta. Nunca el teléfono ni el nombre. Es el único adaptador que registra el código.
 */
@Slf4j
public class LogPhoneVerificationSender implements PhoneVerificationSenderPort {

    @Override
    public Mono<Void> ensureReady() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        return Mono.fromRunnable(() -> log.info(
                "Verification code (VERIFICATION_SENDER=log) tenantId={} verificationId={} channel={} code={}",
                message.tenantId(), message.verificationId(), message.channel().value(), message.code()));
    }
}

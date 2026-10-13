package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.VerificationMessage;
import reactor.core.publisher.Mono;

/** Envío del código al teléfono (WhatsApp). Los adaptadores se eligen con VERIFICATION_SENDER. */
public interface PhoneVerificationSenderPort {

    /** VERIFICATION_UNAVAILABLE si este adaptador no puede enviar (antes de crear la verificación). */
    Mono<Void> ensureReady();

    /** Falla si no se pudo enviar. Nunca registra el teléfono ni (fuera del modo log) el código. */
    Mono<Void> send(VerificationMessage message);
}

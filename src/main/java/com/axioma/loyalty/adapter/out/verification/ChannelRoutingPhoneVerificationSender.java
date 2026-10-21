package com.axioma.loyalty.adapter.out.verification;

import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import java.util.EnumMap;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * VERIFICATION_SENDER=whatsapp|sms: entrega cada mensaje al adaptador de su canal (uno por canal
 * habilitado, creados con sus credenciales al arrancar). Un canal sin adaptador no debería llegar aquí
 * (VerificationChannelPolicy lo rechaza antes); si llega, VERIFICATION_UNAVAILABLE.
 */
public class ChannelRoutingPhoneVerificationSender implements PhoneVerificationSenderPort {

    private final Map<VerificationChannel, PhoneVerificationSenderPort> senders;

    public ChannelRoutingPhoneVerificationSender(Map<VerificationChannel, PhoneVerificationSenderPort> senders) {
        this.senders = new EnumMap<>(senders);
    }

    @Override
    public Mono<Void> ensureReady() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> send(VerificationMessage message) {
        PhoneVerificationSenderPort sender = senders.get(message.channel());
        return sender == null ? Mono.error(VerificationDeliveryException.unavailable(null)) : sender.send(message);
    }
}

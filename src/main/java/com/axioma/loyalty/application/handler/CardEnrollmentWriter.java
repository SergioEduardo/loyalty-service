package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.EnrolledCard;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.port.out.CardRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Transacción corta del 12.7 (paso 4): INSERT ... ON CONFLICT (tenant_id, phone_e164) DO NOTHING y, si
 * ya existía, la lee (conserva su nombre). La lectura es otra sentencia, así que ve la fila que otra
 * alta simultánea acaba de confirmar. Se confirma antes de llamar al wallet-service.
 */
@Component
@RequiredArgsConstructor
public class CardEnrollmentWriter {

    private final CardRepository cards;

    @Transactional
    public Mono<EnrolledCard> enroll(UUID tenantId, PhoneE164 phone, String holderName) {
        return cards.insertIfAbsent(tenantId, phone, holderName)
                .map(card -> new EnrolledCard(card, true))
                .switchIfEmpty(Mono.defer(() -> cards.findByPhone(tenantId, phone)
                        .map(card -> new EnrolledCard(card, false))));
    }
}

package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.PhoneE164;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface CardRepository {

    /** Inserta la tarjeta si el teléfono no tiene una en el negocio. Vacío si ya existía. */
    Mono<Card> insertIfAbsent(UUID tenantId, PhoneE164 phone, String holderName);

    Mono<Card> findByPhone(UUID tenantId, PhoneE164 phone);
}

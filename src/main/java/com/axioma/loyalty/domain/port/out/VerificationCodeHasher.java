package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.PhoneE164;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Hash del código ligado al negocio y al teléfono. Sin sal configurada: VERIFICATION_UNAVAILABLE. */
public interface VerificationCodeHasher {

    Mono<Void> ensureReady();

    String hash(UUID tenantId, PhoneE164 phone, String code);

    /** Comparación en tiempo constante. */
    boolean matches(String expectedHash, UUID tenantId, PhoneE164 phone, String code);
}

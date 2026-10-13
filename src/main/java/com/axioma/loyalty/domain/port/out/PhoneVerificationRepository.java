package com.axioma.loyalty.domain.port.out;

import com.axioma.loyalty.domain.model.IssuedVerification;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.PhoneVerificationState;
import com.axioma.loyalty.domain.model.RecentRequests;
import com.axioma.loyalty.domain.model.VerificationAttempt;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Verificaciones del teléfono (loyalty.phone_verifications). El código solo existe como hash. */
public interface PhoneVerificationRepository {

    /** Bloquea (negocio, teléfono) hasta el fin de la transacción en curso. */
    Mono<Void> lockPhone(UUID tenantId, PhoneE164 phone);

    Mono<RecentRequests> countRecent(UUID tenantId, PhoneE164 phone, int windowMinutes);

    Mono<IssuedVerification> create(UUID tenantId, PhoneE164 phone, String codeHash, int ttlMinutes);

    /** Vacío si no hay una verificación con ese id para ese negocio y teléfono. */
    Mono<PhoneVerificationState> find(UUID verificationId, UUID tenantId, PhoneE164 phone);

    /**
     * Suma un intento de forma atómica si la verificación sigue pendiente, vigente y por debajo del
     * máximo. Vacío si no se pudo contar.
     */
    Mono<VerificationAttempt> recordAttempt(UUID verificationId, UUID tenantId, PhoneE164 phone, int maxAttempts);

    Mono<Void> markVerified(UUID verificationId);
}

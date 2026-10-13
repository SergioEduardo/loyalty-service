package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.IssuedVerification;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.error.RateLimitedException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Crea la verificación si el teléfono no llegó a su límite en la última hora (contado con
 * phone_verifications.created_at). Una transacción con un bloqueo por (negocio, teléfono): dos
 * solicitudes simultáneas no pueden pasar las dos con el último lugar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneVerificationIssuer {

    private final PhoneVerificationRepository verifications;
    private final EnrollProperties properties;

    @Transactional
    public Mono<IssuedVerification> issue(UUID tenantId, PhoneE164 phone, String codeHash) {
        return verifications.lockPhone(tenantId, phone)
                .then(Mono.defer(() -> verifications.countRecent(tenantId, phone, EnrollProperties.WINDOW_MINUTES)))
                .flatMap(recent -> {
                    if (recent.requests() >= properties.phoneLimitPerHour()) {
                        log.info("Enrollment rate limited scope=phone tenantId={}", tenantId);
                        return Mono.error(new RateLimitedException(recent.retryAfterSeconds()));
                    }
                    return verifications.create(tenantId, phone, codeHash, properties.codeTtlMinutes());
                });
    }
}

package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.EnrollmentRequest;
import com.axioma.loyalty.domain.model.PhoneVerificationState;
import com.axioma.loyalty.domain.model.error.CodeAttemptsExhaustedException;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationRepository;
import com.axioma.loyalty.domain.port.out.VerificationCodeHasher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Paso 3 del 12.7. La verificación se busca por id, negocio y teléfono: cualquier discrepancia es
 * INVALID_CODE, sin decir cuál. Vencida: CODE_EXPIRED. Ya validada: vale hasta vencer sin volver a
 * comparar (reintento seguro si falla el wallet-service). Si no, suma el intento de forma atómica
 * ANTES de comparar el hash (tiempo constante); al llegar al máximo: TOO_MANY_ATTEMPTS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeCheck {

    private final PhoneVerificationRepository verifications;
    private final VerificationCodeHasher hasher;
    private final EnrollProperties properties;

    public Mono<Void> verify(UUID tenantId, EnrollmentRequest request) {
        return find(tenantId, request).flatMap(state -> {
            if (state.expired()) {
                return reject(tenantId, "expired", new LoyaltyException(ErrorCode.CODE_EXPIRED));
            }
            if (state.verified()) {
                return Mono.empty();
            }
            if (state.attempts() >= properties.maxAttempts()) {
                return reject(tenantId, "exhausted", new CodeAttemptsExhaustedException());
            }
            return compare(tenantId, request);
        });
    }

    private Mono<PhoneVerificationState> find(UUID tenantId, EnrollmentRequest request) {
        return verifications.find(request.verificationId(), tenantId, request.phone())
                .switchIfEmpty(Mono.defer(() -> reject(tenantId, "not-found",
                        new LoyaltyException(ErrorCode.INVALID_CODE))));
    }

    private Mono<Void> compare(UUID tenantId, EnrollmentRequest request) {
        int max = properties.maxAttempts();
        return verifications.recordAttempt(request.verificationId(), tenantId, request.phone(), max)
                .flatMap(attempt -> {
                    if (hasher.matches(attempt.codeHash(), tenantId, request.phone(), request.code())) {
                        log.info("Phone verified tenantId={} attempts={}", tenantId, attempt.attempts());
                        return verifications.markVerified(request.verificationId());
                    }
                    return reject(tenantId, "wrong-code attempts=" + attempt.attempts(), attempt.attempts() >= max
                            ? new CodeAttemptsExhaustedException()
                            : new LoyaltyException(ErrorCode.INVALID_CODE));
                })
                // No se pudo contar: otra petición la validó, la agotó o venció entre la lectura y el UPDATE.
                .switchIfEmpty(Mono.defer(() -> find(tenantId, request).flatMap(state -> {
                    if (state.verified() && !state.expired()) {
                        return Mono.<Void>empty();
                    }
                    return reject(tenantId, "race", state.expired()
                            ? new LoyaltyException(ErrorCode.CODE_EXPIRED)
                            : new CodeAttemptsExhaustedException());
                })));
    }

    private static <T> Mono<T> reject(UUID tenantId, String reason, LoyaltyException error) {
        log.info("Phone verification rejected tenantId={} reason={}", tenantId, reason);
        return Mono.error(error);
    }
}

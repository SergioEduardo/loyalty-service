package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.EnrollCardCommand;
import com.axioma.loyalty.application.validator.EnrollCardValidator;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.CardEnrollment;
import com.axioma.loyalty.domain.model.EnrollmentRequest;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.port.out.VerificationCodeHasher;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Dar la tarjeta (12.7). Orden: validación (INVALID_NAME, INVALID_PHONE, INVALID_CODE), sal lista,
 * límite de altas por IP, negocio activo por slug, verificación del código (VerificationCodeCheck),
 * tarjeta nueva o existente en una transacción corta ya confirmada (CardEnrollmentWriter) y, fuera de
 * la transacción, IssuePass en el wallet-service. Si éste falla, la tarjeta y la verificación validada
 * se quedan: el cliente reintenta con el mismo código hasta que venza.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollCardHandler implements Handler<EnrollCardCommand, Mono<CardEnrollment>> {

    private final EnrollCardValidator validator;
    private final VerificationCodeHasher hasher;
    private final EnrollRateLimits limits;
    private final ActiveTenantGuard tenantGuard;
    private final VerificationCodeCheck codeCheck;
    private final CardEnrollmentWriter cardWriter;
    private final WalletPassIssuerPort walletPassIssuer;

    @Override
    public Mono<CardEnrollment> handle(EnrollCardCommand command) {
        return validator.validate(command)
                .flatMap(request -> hasher.ensureReady()
                        .then(limits.checkEnrollIp(command.clientIp()))
                        .then(Mono.defer(() -> tenantGuard.requireBySlug(request.slug())))
                        .flatMap(tenant -> enroll(tenant, request)));
    }

    private Mono<CardEnrollment> enroll(Tenant tenant, EnrollmentRequest request) {
        return codeCheck.verify(tenant.id(), request)
                .then(Mono.defer(() -> cardWriter.enroll(tenant.id(), request.phone(), request.holderName())))
                .flatMap(enrolled -> {
                    Card card = enrolled.card();
                    // Una tarjeta previa sin nombre (no la crea este flujo) recibe el del alta solo en el pase.
                    Card forPass = card.holderName() == null
                            ? new Card(card.id(), card.tenantId(), request.holderName(), card.points())
                            : card;
                    return walletPassIssuer.issue(forPass)
                            .map(pass -> {
                                log.info("Card enrolled tenantId={} isNew={}", tenant.id(), enrolled.isNew());
                                return new CardEnrollment(enrolled.isNew(), pass);
                            });
                });
    }
}

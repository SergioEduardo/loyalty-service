package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.model.TenantUpdate;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Cambia un negocio activo y escribe su TenantChanged en la misma transacción. Bloquea la fila
 * mientras tanto, para que dos cambios simultáneos (p. ej. identidad y logo) no se pisen.
 * Si el cambio no altera nada, no escribe ni avisa. La publicación del aviso va después del commit
 * (OutboxFastPath), nunca aquí.
 */
@Component
@RequiredArgsConstructor
public class TenantChangeWriter {

    private final TenantRepository tenantRepository;
    private final TenantEventOutbox outbox;

    @Transactional
    public Mono<TenantUpdate> apply(UUID tenantId, UnaryOperator<Tenant> change) {
        return tenantRepository.findByIdForUpdate(tenantId)
                .filter(Tenant::active)
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)))
                .flatMap(before -> {
                    Tenant after = change.apply(before);
                    if (after.equals(before)) {
                        return Mono.just(TenantUpdate.unchanged(before));
                    }
                    return tenantRepository.update(after)
                            .flatMap(saved -> outbox.append(TenantChanged.from(saved))
                                    .map(eventId -> new TenantUpdate(before, saved, eventId)));
                });
    }
}

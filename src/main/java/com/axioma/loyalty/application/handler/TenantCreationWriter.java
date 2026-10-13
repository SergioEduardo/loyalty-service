package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.CreatedTenant;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** Alta del negocio y su TenantChanged en la misma transacción. */
@Component
@RequiredArgsConstructor
public class TenantCreationWriter {

    private final TenantRepository tenantRepository;
    private final TenantEventOutbox outbox;

    @Transactional
    public Mono<CreatedTenant> create(Tenant tenant) {
        return tenantRepository.create(tenant)
                .flatMap(saved -> outbox.append(TenantChanged.from(saved))
                        .map(eventId -> new CreatedTenant(saved, eventId)));
    }
}

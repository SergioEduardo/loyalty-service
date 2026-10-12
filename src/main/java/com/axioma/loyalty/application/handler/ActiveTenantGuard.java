package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** El negocio de las rutas admin debe existir y estar activo. */
@Component
@RequiredArgsConstructor
public class ActiveTenantGuard {

    private final TenantRepository tenantRepository;

    public Mono<Tenant> require(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(Tenant::active)
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));
    }
}

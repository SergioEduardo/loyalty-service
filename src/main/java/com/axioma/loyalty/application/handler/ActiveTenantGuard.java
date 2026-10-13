package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** El negocio de las rutas admin (por id) o del alta pública (por slug) debe existir y estar activo. */
@Component
@RequiredArgsConstructor
public class ActiveTenantGuard {

    private final TenantRepository tenantRepository;

    public Mono<Tenant> require(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(Tenant::active)
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));
    }

    /** Slug inválido, inexistente o de un negocio inactivo: TENANT_NOT_FOUND. */
    public Mono<Tenant> requireBySlug(String slug) {
        if (!Slug.isValid(slug)) {
            return Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND));
        }
        return tenantRepository.findBySlug(new Slug(slug))
                .filter(Tenant::active)
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));
    }
}

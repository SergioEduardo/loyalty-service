package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.RemoveTenantLogoCommand;
import com.axioma.loyalty.domain.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Quita el logo y borra su objeto. Sin logo no hace nada (idempotente). */
@Service
@RequiredArgsConstructor
public class RemoveTenantLogoHandler implements Handler<RemoveTenantLogoCommand, Mono<Void>> {

    private final TenantChangeWriter tenantChangeWriter;
    private final LogoCleanup logoCleanup;

    @Override
    public Mono<Void> handle(RemoveTenantLogoCommand command) {
        return tenantChangeWriter.apply(command.tenantId(), Tenant::withoutLogo)
                .flatMap(logoCleanup::deleteReplaced);
    }
}

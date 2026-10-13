package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.application.query.GetTenantManifestQuery;
import com.axioma.loyalty.domain.model.TenantManifest;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Manifest de la PWA de caja a partir de la identidad pública. Vacío si el negocio no existe,
 * está desactivado o el slug no es válido: para el navegador todos son "no existe".
 */
@Service
@RequiredArgsConstructor
public class GetTenantManifestHandler
        implements Handler<GetTenantManifestQuery, Mono<TenantManifest>> {

    private final GetTenantIdentityHandler getTenantIdentityHandler;

    @Override
    public Mono<TenantManifest> handle(GetTenantManifestQuery query) {
        return getTenantIdentityHandler.handle(new GetTenantIdentityQuery(query.slug()))
                .map(TenantManifest::of)
                .onErrorResume(GetTenantManifestHandler::isNotFound, e -> Mono.empty());
    }

    private static boolean isNotFound(Throwable error) {
        return error instanceof LoyaltyException e
                && (e.getCode() == ErrorCode.TENANT_NOT_FOUND || e.getCode() == ErrorCode.INVALID_SLUG);
    }
}

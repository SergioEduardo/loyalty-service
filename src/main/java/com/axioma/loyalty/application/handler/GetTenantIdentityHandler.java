package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.application.validator.GetTenantIdentityValidator;
import com.axioma.loyalty.domain.model.TenantIdentity;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantIdentityReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetTenantIdentityHandler
        implements Handler<GetTenantIdentityQuery, Mono<TenantIdentity>> {

    private final GetTenantIdentityValidator validator;
    private final TenantIdentityReader tenantIdentityReader;

    @Override
    public Mono<TenantIdentity> handle(GetTenantIdentityQuery query) {
        return validator.validate(query)
                .then(Mono.defer(() -> tenantIdentityReader.findActiveBySlug(query.slug())))
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));
    }
}

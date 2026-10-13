package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.query.GetTenantProfileQuery;
import com.axioma.loyalty.domain.model.TenantProfile;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.port.out.TenantProfileReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetTenantProfileHandler implements Handler<GetTenantProfileQuery, Mono<TenantProfile>> {

    private final TenantProfileReader tenantProfileReader;

    @Override
    public Mono<TenantProfile> handle(GetTenantProfileQuery query) {
        return tenantProfileReader.findActiveById(query.tenantId())
                .switchIfEmpty(Mono.error(new LoyaltyException(ErrorCode.TENANT_NOT_FOUND)));
    }
}

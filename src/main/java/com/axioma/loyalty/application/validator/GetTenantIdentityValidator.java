package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GetTenantIdentityValidator {

    public Mono<Void> validate(GetTenantIdentityQuery query) {
        if (!Slug.isValid(query.slug())) {
            return Mono.error(new LoyaltyException(ErrorCode.INVALID_SLUG));
        }
        return Mono.empty();
    }
}

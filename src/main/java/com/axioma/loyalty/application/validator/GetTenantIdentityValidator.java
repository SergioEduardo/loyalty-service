package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GetTenantIdentityValidator {

    /** Minúsculas, números y guiones; sin guion al inicio ni al final; hasta 50 caracteres. */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int SLUG_MAX_LENGTH = 50;

    public Mono<Void> validate(GetTenantIdentityQuery query) {
        String slug = query.slug();
        if (slug == null || slug.length() > SLUG_MAX_LENGTH || !SLUG.matcher(slug).matches()) {
            return Mono.error(new LoyaltyException(ErrorCode.INVALID_SLUG));
        }
        return Mono.empty();
    }
}

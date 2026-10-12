package com.axioma.loyalty.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.domain.model.Slug;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.UUID;
import java.util.function.Consumer;

final class AdminFixtures {

    static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AdminFixtures() {
    }

    static Tenant tenant(boolean active) {
        return new Tenant(TENANT_ID, new Slug("mi-cafe"), "Mi Café", null, null, "#112233", 10,
                "Café gratis", 0, 300, active);
    }

    static UserAccount user() {
        return new UserAccount(USER_ID, TENANT_ID, "Ana", "ana", "$2a$hash", UserRole.CASHIER, true);
    }

    static Consumer<Throwable> hasCode(ErrorCode code) {
        return error -> {
            assertThat(error).isInstanceOf(LoyaltyException.class);
            assertThat(((LoyaltyException) error).getCode()).isEqualTo(code);
        };
    }
}

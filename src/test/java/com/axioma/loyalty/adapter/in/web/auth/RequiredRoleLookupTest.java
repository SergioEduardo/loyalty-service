package com.axioma.loyalty.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class RequiredRoleLookupTest {

    static class OpenController {
        public void open() {
        }

        @RequireRole(UserRole.OWNER)
        public void ownerOnly() {
        }
    }

    @RequireRole(UserRole.CASHIER)
    static class CashierController {
        public void inherited() {
        }

        @RequireRole(UserRole.OWNER)
        public void stricter() {
        }
    }

    private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(method));
    }

    @Test
    void readsRoleFromMethodOrController() throws Exception {
        assertThat(RequiredRoleLookup.requiredRole(handler(new OpenController(), "open"))).isEmpty();
        assertThat(RequiredRoleLookup.requiredRole(handler(new OpenController(), "ownerOnly")))
                .contains(UserRole.OWNER);
        assertThat(RequiredRoleLookup.requiredRole(handler(new CashierController(), "inherited")))
                .contains(UserRole.CASHIER);
        assertThat(RequiredRoleLookup.requiredRole(handler(new CashierController(), "stricter")))
                .contains(UserRole.OWNER);
        assertThat(RequiredRoleLookup.requiredRole("no-es-un-handler-method")).isEmpty();
    }
}

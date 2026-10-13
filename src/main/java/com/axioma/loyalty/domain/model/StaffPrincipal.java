package com.axioma.loyalty.domain.model;

import java.util.UUID;

/** Quién hace la petición de staff, según su access token y su sesión vigente. */
public record StaffPrincipal(UUID userId, UUID tenantId, UserRole role, UUID sessionId) {

    /** OWNER incluye todo lo que puede hacer CASHIER. */
    public boolean hasRole(UserRole required) {
        return role == UserRole.OWNER || role == required;
    }
}

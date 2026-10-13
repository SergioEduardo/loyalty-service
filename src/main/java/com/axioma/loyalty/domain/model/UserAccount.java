package com.axioma.loyalty.domain.model;

import java.util.UUID;

/**
 * Cuenta del personal de un negocio. Sin credentialHash está pendiente de activación: no puede
 * iniciar sesión hasta elegir su contraseña con el enlace. toString nunca incluye el hash.
 */
public record UserAccount(
        UUID id,
        UUID tenantId,
        String name,
        String username,
        String credentialHash,
        UserRole role,
        boolean active) {

    public boolean pendingActivation() {
        return credentialHash == null;
    }

    public UserAccount deactivate() {
        return new UserAccount(id, tenantId, name, username, credentialHash, role, false);
    }

    public UserAccount withCredentialHash(String newHash) {
        return new UserAccount(id, tenantId, name, username, newHash, role, active);
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", tenantId=" + tenantId + ", username=" + username
                + ", role=" + role + ", active=" + active + "]";
    }
}

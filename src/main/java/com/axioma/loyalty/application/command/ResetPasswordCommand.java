package com.axioma.loyalty.application.command;

import java.util.UUID;

/** toString nunca incluye la contraseña. */
public record ResetPasswordCommand(UUID tenantId, UUID userId, String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordCommand[tenantId=" + tenantId + ", userId=" + userId + ", newPassword=***]";
    }
}

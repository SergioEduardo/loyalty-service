package com.axioma.loyalty.application.command;

import java.util.UUID;

public record DeactivateUserCommand(UUID tenantId, UUID userId) {

    @Override
    public String toString() {
        return "DeactivateUserCommand[tenantId=" + tenantId + ", userId=" + userId + "]";
    }
}

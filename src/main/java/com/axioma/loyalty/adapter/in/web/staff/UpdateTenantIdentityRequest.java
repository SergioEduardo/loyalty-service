package com.axioma.loyalty.adapter.in.web.staff;

import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import java.util.UUID;

/** Sin tenantId ni slug: si el cliente los manda, se ignoran. */
public record UpdateTenantIdentityRequest(String name, String description, String brandColor) {

    UpdateTenantIdentityCommand toCommand(UUID tenantId) {
        return new UpdateTenantIdentityCommand(tenantId, name, description, brandColor);
    }
}

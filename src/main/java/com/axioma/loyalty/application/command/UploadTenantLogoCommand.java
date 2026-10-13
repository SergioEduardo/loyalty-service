package com.axioma.loyalty.application.command;

import java.util.UUID;

/** El negocio sale del token. toString no incluye el contenido. */
public record UploadTenantLogoCommand(UUID tenantId, byte[] content) {

    @Override
    public String toString() {
        return "UploadTenantLogoCommand[tenantId=" + tenantId + ", bytes="
                + (content == null ? 0 : content.length) + "]";
    }
}

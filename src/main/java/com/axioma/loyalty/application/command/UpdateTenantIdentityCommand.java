package com.axioma.loyalty.application.command;

import com.axioma.loyalty.application.validator.TextRules;
import java.util.Locale;
import java.util.UUID;

/** El negocio sale del token. Los textos llegan recortados y el color en minúsculas. */
public record UpdateTenantIdentityCommand(UUID tenantId, String name, String description, String brandColor) {

    public UpdateTenantIdentityCommand {
        name = TextRules.clean(name);
        description = TextRules.clean(description);
        brandColor = TextRules.clean(brandColor);
        brandColor = brandColor == null ? null : brandColor.toLowerCase(Locale.ROOT);
    }
}

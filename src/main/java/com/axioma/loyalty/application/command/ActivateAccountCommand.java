package com.axioma.loyalty.application.command;

import java.util.Locale;

/**
 * Elegir contraseña con un enlace de activación. clientIp puede ser null. toString nunca incluye
 * el token ni la contraseña.
 */
public record ActivateAccountCommand(String slug, String token, String password, String clientIp) {

    public ActivateAccountCommand {
        slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
        token = token == null ? null : token.strip();
    }

    @Override
    public String toString() {
        return "ActivateAccountCommand[slug=" + slug + ", token=***, password=***]";
    }
}

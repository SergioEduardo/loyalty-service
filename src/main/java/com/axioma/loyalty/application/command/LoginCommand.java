package com.axioma.loyalty.application.command;

import java.util.Locale;

/** Slug y usuario llegan recortados y en minúsculas. toString nunca incluye la contraseña. */
public record LoginCommand(String slug, String username, String password) {

    public LoginCommand {
        slug = slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginCommand[slug=" + slug + ", username=" + username + ", password=***]";
    }
}

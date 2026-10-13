package com.axioma.loyalty.application.command;

import com.axioma.loyalty.application.validator.TextRules;
import java.util.Locale;
import java.util.UUID;

/**
 * Alta de una cuenta del personal. El usuario se guarda recortado y en minúsculas; si no
 * llega nombre se usa el usuario. Sin contraseña (null) la cuenta queda pendiente de activación.
 * toString nunca incluye la contraseña.
 */
public record CreateUserCommand(
        UUID tenantId,
        String name,
        String username,
        String password,
        String role) {

    public CreateUserCommand {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        name = TextRules.clean(name);
        if (name == null) {
            name = username;
        }
        role = role == null ? null : role.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "CreateUserCommand[tenantId=" + tenantId + ", username=" + username
                + ", role=" + role + ", password=***]";
    }
}

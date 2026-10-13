package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.application.command.LoginCommand;

/** toString nunca incluye la contraseña. */
public record LoginRequest(String slug, String username, String password) {

    LoginCommand toCommand() {
        return new LoginCommand(slug, username, password);
    }

    @Override
    public String toString() {
        return "LoginRequest[slug=" + slug + ", username=" + username + ", password=***]";
    }
}

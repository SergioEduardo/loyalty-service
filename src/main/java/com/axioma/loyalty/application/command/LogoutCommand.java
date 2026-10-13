package com.axioma.loyalty.application.command;

/** toString nunca incluye el token. */
public record LogoutCommand(String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[refreshToken=***]";
    }
}

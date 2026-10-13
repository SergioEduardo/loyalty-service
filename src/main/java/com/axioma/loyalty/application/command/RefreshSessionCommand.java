package com.axioma.loyalty.application.command;

/** toString nunca incluye el token. */
public record RefreshSessionCommand(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshSessionCommand[refreshToken=***]";
    }
}

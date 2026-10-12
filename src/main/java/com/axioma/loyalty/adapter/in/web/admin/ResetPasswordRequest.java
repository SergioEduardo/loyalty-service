package com.axioma.loyalty.adapter.in.web.admin;

/** toString nunca incluye la contraseña. */
public record ResetPasswordRequest(String password) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[password=***]";
    }
}

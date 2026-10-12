package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.application.command.CreateUserCommand;
import java.util.UUID;

/** name es opcional: si no llega se usa el usuario. toString nunca incluye la contraseña. */
public record CreateUserRequest(String username, String password, String role, String name) {

    CreateUserCommand toCommand(UUID tenantId) {
        return new CreateUserCommand(tenantId, name, username, password, role);
    }

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + ", password=***]";
    }
}

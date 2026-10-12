package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.domain.model.UserAccount;
import java.util.UUID;

public record UserResponse(UUID id, UUID tenantId, String name, String username, String role, boolean active) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.id(), user.tenantId(), user.name(), user.username(),
                user.role().name(), user.active());
    }
}

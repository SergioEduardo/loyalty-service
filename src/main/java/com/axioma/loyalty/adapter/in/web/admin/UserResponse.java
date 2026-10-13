package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.domain.model.CreatedUser;
import com.axioma.loyalty.domain.model.UserAccount;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** activation solo aparece al crear una cuenta sin contraseña (pendiente de activación). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        UUID tenantId,
        String name,
        String username,
        String role,
        boolean active,
        ActivationLinkResponse activation) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.id(), user.tenantId(), user.name(), user.username(),
                user.role().name(), user.active(), null);
    }

    static UserResponse from(CreatedUser created) {
        UserAccount user = created.user();
        return new UserResponse(user.id(), user.tenantId(), user.name(), user.username(),
                user.role().name(), user.active(), ActivationLinkResponse.from(created.activation()));
    }
}

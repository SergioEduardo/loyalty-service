package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.domain.model.StaffPrincipal;
import java.util.UUID;

public record MeResponse(UUID userId, UUID tenantId, String role, UUID sessionId) {

    static MeResponse from(StaffPrincipal principal) {
        return new MeResponse(principal.userId(), principal.tenantId(), principal.role().name(),
                principal.sessionId());
    }
}

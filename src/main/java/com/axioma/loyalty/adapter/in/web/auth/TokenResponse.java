package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.domain.model.AuthSession;
import java.util.UUID;

/** toString nunca incluye los tokens. */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user,
        TenantInfo tenant) {

    public record UserInfo(UUID id, String name, String role) {
    }

    public record TenantInfo(String slug, String name) {
    }

    static TokenResponse from(AuthSession session) {
        var owner = session.owner();
        return new TokenResponse(
                session.accessToken(),
                session.refreshToken(),
                "Bearer",
                session.expiresInSeconds(),
                new UserInfo(owner.userId(), owner.userName(), owner.role().name()),
                new TenantInfo(owner.tenantSlug(), owner.tenantName()));
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}

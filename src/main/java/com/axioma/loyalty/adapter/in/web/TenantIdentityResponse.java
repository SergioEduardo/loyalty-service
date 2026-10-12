package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.TenantIdentity;

public record TenantIdentityResponse(
        String slug,
        String name,
        String description,
        String logoUrl,
        String brandColor) {

    static TenantIdentityResponse from(TenantIdentity identity) {
        return new TenantIdentityResponse(
                identity.slug(),
                identity.name(),
                identity.description(),
                identity.logoUrl(),
                identity.brandColor());
    }
}

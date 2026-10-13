package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.domain.model.ActivationTarget;

public record ActivationInfoResponse(String tenantName, String username) {

    static ActivationInfoResponse from(ActivationTarget target) {
        return new ActivationInfoResponse(target.tenantName(), target.username());
    }
}

package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.application.handler.GetTenantIdentityHandler;
import com.axioma.loyalty.application.query.GetTenantIdentityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Identidad pública del negocio: la usan la página de acceso y la de alta. */
@RestController
@RequestMapping("/api/public/tenants")
@RequiredArgsConstructor
public class TenantIdentityController {

    private final GetTenantIdentityHandler getTenantIdentityHandler;

    @GetMapping("/{slug}/identity")
    public Mono<TenantIdentityResponse> getIdentity(@PathVariable String slug) {
        return getTenantIdentityHandler
                .handle(new GetTenantIdentityQuery(slug))
                .map(TenantIdentityResponse::from);
    }
}

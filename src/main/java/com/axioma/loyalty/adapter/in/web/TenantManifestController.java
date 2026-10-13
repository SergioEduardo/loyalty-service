package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.application.handler.GetTenantManifestHandler;
import com.axioma.loyalty.application.query.GetTenantManifestQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Manifest de la PWA de caja por negocio. Público y fuera de /api: Firebase Hosting reenvía
 * /caja/{slug}/manifest.webmanifest a este servicio con la ruta original.
 */
@RestController
@RequiredArgsConstructor
public class TenantManifestController {

    static final MediaType MANIFEST_JSON = MediaType.parseMediaType("application/manifest+json");
    static final String CACHE_CONTROL = "public, max-age=300";

    private final GetTenantManifestHandler getTenantManifestHandler;

    @GetMapping("/caja/{slug}/manifest.webmanifest")
    public Mono<ResponseEntity<TenantManifestResponse>> getManifest(@PathVariable String slug) {
        return getTenantManifestHandler.handle(new GetTenantManifestQuery(slug))
                .map(manifest -> ResponseEntity.ok()
                        .contentType(MANIFEST_JSON)
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                        .body(TenantManifestResponse.from(manifest)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}

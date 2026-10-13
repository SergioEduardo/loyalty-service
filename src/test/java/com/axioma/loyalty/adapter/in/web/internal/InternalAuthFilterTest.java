package com.axioma.loyalty.adapter.in.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.PathContainer;

class InternalAuthFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"/internal", "/internal/outbox/publish", "//internal/outbox/publish",
            "/%69nternal/outbox/publish", "/internal;x=1/outbox/publish", "/internal/otro"})
    void protectsInternalPathsIncludingEncodedVariants(String path) {
        assertThat(InternalAuthFilter.isInternalPath(PathContainer.parsePath(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/tenants", "/caja/mi-cafe/manifest.webmanifest", "/internals", "/actuator/health", "/"})
    void ignoresOtherPaths(String path) {
        assertThat(InternalAuthFilter.isInternalPath(PathContainer.parsePath(path))).isFalse();
    }
}

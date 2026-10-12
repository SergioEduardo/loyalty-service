package com.axioma.loyalty.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.PathContainer;

class AdminAuthFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin", "/api/admin/tenants", "//api/admin/tenants", "/api/%61dmin/tenants",
            "/api/admin;x=1/tenants", "/api/admin/"})
    void protectsAdminPathsIncludingEncodedVariants(String path) {
        assertThat(AdminAuthFilter.isAdminPath(PathContainer.parsePath(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/public/tenants/mi-cafe/identity", "/api/administracion", "/actuator/health", "/"})
    void ignoresOtherPaths(String path) {
        assertThat(AdminAuthFilter.isAdminPath(PathContainer.parsePath(path))).isFalse();
    }
}

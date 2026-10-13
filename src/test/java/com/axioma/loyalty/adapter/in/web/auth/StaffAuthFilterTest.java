package com.axioma.loyalty.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.PathContainer;

class StaffAuthFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/staff/tenant", "//api/staff/tenant", "/api/%73taff/tenant", "/api/auth/me"})
    void protectsStaffPaths(String path) {
        assertThat(StaffAuthFilter.isStaffPath(PathContainer.parsePath(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/caja/mi-cafe/manifest.webmanifest", "/caja/mi-cafe", "/api/public/tenants/mi-cafe/identity",
            "/api/auth/login", "/api/auth/refresh"})
    void ignoresPublicPaths(String path) {
        assertThat(StaffAuthFilter.isStaffPath(PathContainer.parsePath(path))).isFalse();
    }
}

package com.axioma.loyalty.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureRefreshTokenGeneratorTest {

    private final SecureRefreshTokenGenerator generator = new SecureRefreshTokenGenerator();

    @Test
    void generates256BitBase64UrlTokens() {
        String token = generator.generate();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(generator.generate()).isNotEqualTo(token);
    }

    @Test
    void hashesWithSha256Hex() {
        assertThat(generator.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}

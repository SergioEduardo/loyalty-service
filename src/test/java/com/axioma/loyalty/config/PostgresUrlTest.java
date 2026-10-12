package com.axioma.loyalty.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresUrlTest {

    @Test
    void parsesNeonUrl() {
        var url = PostgresUrl.parse(
                "postgresql://loyalty_owner:s3cr3t@ep-abc-123.c-14.us-east-1.aws.neon.tech/loyalty?sslmode=require");

        assertThat(url.host()).isEqualTo("ep-abc-123.c-14.us-east-1.aws.neon.tech");
        assertThat(url.port()).isEqualTo(5432);
        assertThat(url.database()).isEqualTo("loyalty");
        assertThat(url.username()).isEqualTo("loyalty_owner");
        assertThat(url.password()).isEqualTo("s3cr3t");
        assertThat(url.sslRequired()).isTrue();
        assertThat(url.jdbcUrl())
                .isEqualTo("jdbc:postgresql://ep-abc-123.c-14.us-east-1.aws.neon.tech:5432/loyalty?sslmode=require");
    }

    @Test
    void parsesLocalUrlWithoutSsl() {
        var url = PostgresUrl.parse("postgresql://loyalty:loyalty@localhost:5433/loyalty?sslmode=disable");

        assertThat(url.port()).isEqualTo(5433);
        assertThat(url.sslRequired()).isFalse();
    }

    @Test
    void decodesPercentEncodedPassword() {
        var url = PostgresUrl.parse("postgresql://u:p%40ss%3Aword@host.example/db");

        assertThat(url.password()).isEqualTo("p@ss:word");
    }

    @Test
    void toStringNeverShowsThePassword() {
        var url = PostgresUrl.parse("postgresql://u:topsecret@host.example/db");

        assertThat(url.toString()).doesNotContain("topsecret");
    }

    @Test
    void errorsDoNotLeakTheUrl() {
        assertThatThrownBy(() -> PostgresUrl.parse("mysql://u:topsecret@host/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("topsecret");
        assertThatThrownBy(() -> PostgresUrl.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostgresUrl.parse("postgresql://host.example/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

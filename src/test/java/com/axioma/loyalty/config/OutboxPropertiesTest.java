package com.axioma.loyalty.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

    @Test
    void emptyValuesTakeTheirDefaultsAndDisablePublishing() {
        var properties = new OutboxProperties(" ", null, null, null, null, null, null, List.of(""));

        assertThat(properties.publishingEnabled()).isFalse();
        assertThat(properties.publishEndpointConfigured()).isFalse();
        assertThat(properties.batchSize()).isEqualTo(50);
        assertThat(properties.retryPolicy().maxAttempts()).isEqualTo(8);
        assertThat(properties.fastPathTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.claimLease()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void normalizesTheSchedulerIdentity() {
        var properties = new OutboxProperties("projects/p/topics/loyalty-events", null, null, null, null, null,
                " https://svc.run.app ", List.of(" SA-Scheduler@p.iam.gserviceaccount.com "));

        assertThat(properties.publishingEnabled()).isTrue();
        assertThat(properties.publishAudience()).isEqualTo("https://svc.run.app");
        assertThat(properties.publishAllowedEmails()).containsExactly("sa-scheduler@p.iam.gserviceaccount.com");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new OutboxProperties("loyalty-events", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(null, 0, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(null, null, 0, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(null, null, null, 0L, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // El plazo del reclamo debe cubrir la publicación.
        assertThatThrownBy(() -> new OutboxProperties(null, null, null, null, 30_000L, 20L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

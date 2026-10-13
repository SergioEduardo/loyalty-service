package com.axioma.loyalty.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    private final OutboxRetryPolicy policy = new OutboxRetryPolicy(8);

    @Test
    void delayDoublesFromFiveSecondsUpToFifteenMinutes() {
        assertThat(policy.afterFailure(0).retryDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.afterFailure(1).retryDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.afterFailure(2).retryDelay()).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.afterFailure(7).retryDelay()).isEqualTo(Duration.ofSeconds(640));
        assertThat(policy.afterFailure(8).retryDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.afterFailure(1000).retryDelay()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void theEighthFailureExhaustsTheEvent() {
        assertThat(policy.afterFailure(6)).isEqualTo(new OutboxRetryPolicy.Decision(7, false, Duration.ofSeconds(320)));
        assertThat(policy.afterFailure(7).exhausted()).isTrue();
        assertThat(policy.afterFailure(7).attempts()).isEqualTo(8);
    }

    @Test
    void needsAtLeastOneAttempt() {
        assertThatThrownBy(() -> new OutboxRetryPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.axioma.loyalty.config;

import com.axioma.loyalty.adapter.out.messaging.PubSubEventAdapter;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.Publisher;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class PubSubConfig {

    /**
     * Sin PUBSUB_TOPIC, la publicación queda desactivada (el outbox solo acumula) para poder desplegar
     * antes de que exista la infraestructura. Con tópico, un Publisher con credenciales de ADC cuyos
     * reintentos internos no pasan de OUTBOX_PUBLISH_TIMEOUT_MS.
     */
    @Bean
    public PubSubEventAdapter eventPublisher(OutboxProperties properties) throws IOException {
        if (!properties.publishingEnabled()) {
            log.warn("PUBSUB_TOPIC vacío: la publicación de avisos está desactivada");
            return PubSubEventAdapter.disabled();
        }
        return new PubSubEventAdapter(Publisher.newBuilder(properties.topic())
                .setRetrySettings(retrySettings(properties.publishTimeout()))
                .build());
    }

    static RetrySettings retrySettings(Duration total) {
        Duration rpc = total.compareTo(Duration.ofSeconds(5)) < 0 ? total : Duration.ofSeconds(5);
        // Como los defaults del Publisher (reintento desde 100 ms ×4), con su tope de 600 s acortado.
        return RetrySettings.newBuilder()
                .setInitialRetryDelayDuration(Duration.ofMillis(100))
                .setRetryDelayMultiplier(4.0)
                .setMaxRetryDelayDuration(Duration.ofSeconds(2))
                .setInitialRpcTimeoutDuration(rpc)
                .setRpcTimeoutMultiplier(4.0)
                .setMaxRpcTimeoutDuration(total)
                .setTotalTimeoutDuration(total)
                .build();
    }
}

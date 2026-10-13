package com.axioma.loyalty.config;

import com.axioma.loyalty.domain.model.OutboxRetryPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Publicación de avisos del outbox en Pub/Sub. Sin topic (PUBSUB_TOPIC vacío) la publicación queda
 * desactivada. publishAudience y publishAllowedEmails: quién puede llamar a /internal/outbox/publish
 * (la identidad de Cloud Scheduler); si falta cualquiera de los dos, el endpoint queda cerrado.
 * Los números vacíos (una variable de GitHub sin definir llega como "") toman su default.
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        String topic,
        Integer batchSize,
        Integer maxAttempts,
        Long fastPathTimeoutMs,
        Long publishTimeoutMs,
        Long claimLeaseSeconds,
        String publishAudience,
        List<String> publishAllowedEmails) {

    public static final int MAX_BATCH_SIZE = 500;
    private static final Pattern TOPIC = Pattern.compile("^projects/[^/]+/topics/[^/]+$");

    public OutboxProperties {
        topic = topic == null ? "" : topic.strip();
        batchSize = batchSize == null ? 50 : batchSize;
        maxAttempts = maxAttempts == null ? 8 : maxAttempts;
        fastPathTimeoutMs = fastPathTimeoutMs == null ? 1000L : fastPathTimeoutMs;
        publishTimeoutMs = publishTimeoutMs == null ? 10_000L : publishTimeoutMs;
        claimLeaseSeconds = claimLeaseSeconds == null ? 120L : claimLeaseSeconds;
        publishAudience = publishAudience == null ? "" : publishAudience.strip();
        publishAllowedEmails = publishAllowedEmails == null ? List.of() : publishAllowedEmails.stream()
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .toList();

        if (!topic.isEmpty() && !TOPIC.matcher(topic).matches()) {
            throw new IllegalArgumentException("PUBSUB_TOPIC debe tener la forma projects/{proyecto}/topics/{tópico}");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("OUTBOX_BATCH_SIZE debe estar entre 1 y " + MAX_BATCH_SIZE);
        }
        if (fastPathTimeoutMs <= 0 || publishTimeoutMs <= 0) {
            throw new IllegalArgumentException("Los tiempos máximos del outbox deben ser positivos");
        }
        // El plazo del reclamo debe cubrir la publicación del aviso, o otra ejecución lo tomaría a la vez.
        if (claimLeaseSeconds * 1000 <= publishTimeoutMs) {
            throw new IllegalArgumentException("OUTBOX_CLAIM_LEASE_SECONDS debe ser mayor que OUTBOX_PUBLISH_TIMEOUT_MS");
        }
        new OutboxRetryPolicy(maxAttempts); // valida maxAttempts
    }

    public boolean publishingEnabled() {
        return !topic.isEmpty();
    }

    public boolean publishEndpointConfigured() {
        return !publishAudience.isEmpty() && !publishAllowedEmails.isEmpty();
    }

    public OutboxRetryPolicy retryPolicy() {
        return new OutboxRetryPolicy(maxAttempts);
    }

    public Duration fastPathTimeout() {
        return Duration.ofMillis(fastPathTimeoutMs);
    }

    public Duration publishTimeout() {
        return Duration.ofMillis(publishTimeoutMs);
    }

    public Duration claimLease() {
        return Duration.ofSeconds(claimLeaseSeconds);
    }
}

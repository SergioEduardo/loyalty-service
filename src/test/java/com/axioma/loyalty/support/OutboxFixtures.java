package com.axioma.loyalty.support;

import com.axioma.loyalty.application.handler.OutboxFastPath;
import com.axioma.loyalty.application.handler.PublishPendingEventsHandler;
import com.axioma.loyalty.application.validator.PublishPendingEventsValidator;
import com.axioma.loyalty.config.OutboxProperties;
import com.axioma.loyalty.domain.port.out.EventPublisherPort;
import com.axioma.loyalty.domain.port.out.OutboxEventRepository;

/** Arma el caso de uso de publicación y el camino rápido sobre fakes. */
public final class OutboxFixtures {

    private OutboxFixtures() {
    }

    public static OutboxProperties properties(int maxAttempts, long fastPathTimeoutMs, long publishTimeoutMs) {
        return new OutboxProperties(null, null, maxAttempts, fastPathTimeoutMs, publishTimeoutMs, null, null, null);
    }

    public static OutboxProperties defaults() {
        return new OutboxProperties(null, null, null, null, null, null, null, null);
    }

    public static PublishPendingEventsHandler handler(OutboxEventRepository outbox, EventPublisherPort publisher,
                                                      OutboxProperties properties) {
        return new PublishPendingEventsHandler(new PublishPendingEventsValidator(), outbox, publisher, properties);
    }

    public static OutboxFastPath fastPath(OutboxEventRepository outbox, EventPublisherPort publisher,
                                          OutboxProperties properties) {
        return new OutboxFastPath(handler(outbox, publisher, properties), properties);
    }

    /** Camino rápido sin tópico: no publica nada. */
    public static OutboxFastPath disabledFastPath() {
        return fastPath(new InMemoryOutboxEvents(), new FakeEventPublisher(false), defaults());
    }
}

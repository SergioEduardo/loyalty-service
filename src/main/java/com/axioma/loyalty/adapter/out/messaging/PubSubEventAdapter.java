package com.axioma.loyalty.adapter.out.messaging;

import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.port.out.EventPublisherPort;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Mono;

/**
 * Publica avisos en el tópico de Pub/Sub (PUBSUB_TOPIC). Cuerpo: el payload JSON. Atributos: eventType,
 * tenantId, cardId (cadena vacía si no hay) y seq (el id del aviso). Sin Publisher (sin tópico) queda
 * desactivado. Lo crea PubSubConfig; las credenciales salen de ADC.
 */
@Slf4j
public class PubSubEventAdapter implements EventPublisherPort, DisposableBean {

    static final String EVENT_TYPE = "eventType";
    static final String TENANT_ID = "tenantId";
    static final String CARD_ID = "cardId";
    static final String SEQ = "seq";

    private final Publisher publisher;

    public PubSubEventAdapter(Publisher publisher) {
        this.publisher = publisher;
    }

    public static PubSubEventAdapter disabled() {
        return new PubSubEventAdapter(null);
    }

    @Override
    public boolean isEnabled() {
        return publisher != null;
    }

    @Override
    public Mono<Void> publish(OutboxEvent event) {
        if (publisher == null) {
            return Mono.error(new IllegalStateException("La publicación en Pub/Sub está desactivada"));
        }
        return Mono.defer(() -> confirmed(publisher.publish(toMessage(event)))).then();
    }

    static PubsubMessage toMessage(OutboxEvent event) {
        return PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(event.payload(), StandardCharsets.UTF_8))
                .putAttributes(EVENT_TYPE, event.eventType())
                .putAttributes(TENANT_ID, event.tenantId().toString())
                .putAttributes(CARD_ID, event.cardId() == null ? "" : event.cardId().toString())
                .putAttributes(SEQ, Long.toString(event.id()))
                .build();
    }

    private static Mono<String> confirmed(ApiFuture<String> future) {
        return Mono.create(sink -> ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onSuccess(String messageId) {
                sink.success(messageId);
            }

            @Override
            public void onFailure(Throwable error) {
                sink.error(error);
            }
        }, MoreExecutors.directExecutor()));
    }

    /** Envía lo que quede en el lote antes de apagar. */
    @Override
    public void destroy() throws InterruptedException {
        if (publisher != null) {
            publisher.shutdown();
            if (!publisher.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Pub/Sub publisher did not terminate in time");
            }
        }
    }
}

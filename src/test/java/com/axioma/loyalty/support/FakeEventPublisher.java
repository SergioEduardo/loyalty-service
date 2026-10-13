package com.axioma.loyalty.support;

import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.port.out.EventPublisherPort;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Mono;

/** Fake de EventPublisherPort: confirma, falla o no responde, y recuerda lo que confirmó. */
public class FakeEventPublisher implements EventPublisherPort {

    public enum Mode { CONFIRM, FAIL, HANG }

    private final boolean enabled;
    private volatile Mode mode = Mode.CONFIRM;
    private volatile Duration delay = Duration.ZERO;
    private final List<OutboxEvent> confirmed = new CopyOnWriteArrayList<>();
    private final List<Long> attempted = new CopyOnWriteArrayList<>();

    public FakeEventPublisher(boolean enabled) {
        this.enabled = enabled;
    }

    public FakeEventPublisher mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /** Demora antes de confirmar o fallar. */
    public FakeEventPublisher delay(Duration delay) {
        this.delay = delay;
        return this;
    }

    public List<OutboxEvent> confirmed() {
        return confirmed;
    }

    public List<Long> confirmedIds() {
        return confirmed.stream().map(OutboxEvent::id).toList();
    }

    public List<Long> attemptedIds() {
        return attempted;
    }

    public void reset() {
        mode = Mode.CONFIRM;
        delay = Duration.ZERO;
        confirmed.clear();
        attempted.clear();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Mono<Void> publish(OutboxEvent event) {
        return Mono.defer(() -> {
            attempted.add(event.id());
            Mono<Void> result = switch (mode) {
                case CONFIRM -> Mono.fromRunnable(() -> confirmed.add(event));
                case FAIL -> Mono.error(new IllegalStateException("Pub/Sub no disponible"));
                case HANG -> Mono.never();
            };
            return delay.isZero() ? result : Mono.delay(delay).then(result);
        });
    }
}

package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.ErrorAssertions.hasCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.application.handler.PublishPendingEventsHandler;
import com.axioma.loyalty.domain.model.OutboxEvent;
import com.axioma.loyalty.domain.model.PublishReport;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.support.FakeEventPublisher;
import com.axioma.loyalty.support.InMemoryOutboxEvents;
import com.axioma.loyalty.support.OutboxFixtures;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** PublishPendingEventsHandler con un fake de EventPublisherPort y un outbox en memoria. */
class PublishPendingEventsHandlerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InMemoryOutboxEvents outbox = new InMemoryOutboxEvents();
    private final FakeEventPublisher publisher = new FakeEventPublisher(true);
    private final PublishPendingEventsHandler handler =
            OutboxFixtures.handler(outbox, publisher, OutboxFixtures.properties(3, 1000, 200));

    private long[] addEvents(int count) {
        return LongStream.range(0, count).map(i -> outbox.add(TENANT, "TenantChanged", "{\"n\":" + i + "}")).toArray();
    }

    @Test
    void publishesTheBatchAndMarksEachEventPublished() {
        long[] ids = addEvents(3);

        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(3, 3, 0, false))
                .verifyComplete();

        assertThat(publisher.confirmedIds()).containsExactlyInAnyOrder(ids[0], ids[1], ids[2]);
        assertThat(Arrays.stream(ids).mapToObj(id -> outbox.row(id).status())).containsOnly("PUBLISHED");
        // Nada pendiente: la siguiente ejecución no reclama nada.
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(0, 0, 0, false))
                .verifyComplete();
    }

    @Test
    void respectsTheBatchSize() {
        addEvents(5);

        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(2)))
                .expectNext(new PublishReport(2, 2, 0, false))
                .verifyComplete();
        assertThat(publisher.confirmedIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void failureCountsAnAttemptAndSchedulesARetryWithBackoff() {
        long id = addEvents(1)[0];
        publisher.mode(FakeEventPublisher.Mode.FAIL);

        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(1, 0, 1, false))
                .verifyComplete();
        assertThat(outbox.row(id).status()).isEqualTo("PENDING");
        assertThat(outbox.row(id).event().attempts()).isEqualTo(1);
        assertThat(outbox.row(id).lastRetryDelay()).isEqualTo(Duration.ofSeconds(5));

        handler.handle(PublishPendingEventsCommand.due(50)).block();
        assertThat(outbox.row(id).event().attempts()).isEqualTo(2);
        assertThat(outbox.row(id).lastRetryDelay()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void reachingMaxAttemptsMarksTheEventFailed() {
        long id = addEvents(1)[0];
        publisher.mode(FakeEventPublisher.Mode.FAIL);

        for (int i = 0; i < 3; i++) {
            handler.handle(PublishPendingEventsCommand.due(50)).block();
        }

        assertThat(outbox.row(id).status()).isEqualTo("FAILED");
        assertThat(outbox.row(id).event().attempts()).isEqualTo(3);
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(0, 0, 0, false))
                .verifyComplete();
        assertThat(publisher.attemptedIds()).hasSize(3);
    }

    @Test
    void unansweredPublishTimesOutAndCountsAsFailure() {
        long id = addEvents(1)[0];
        publisher.mode(FakeEventPublisher.Mode.HANG);

        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(1, 0, 1, false))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        assertThat(outbox.row(id).event().attempts()).isEqualTo(1);
    }

    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        addEvents(2);
        // El primero falla; los demás se confirman.
        var failingFirst = new FakeEventPublisher(true) {
            @Override
            public Mono<Void> publish(OutboxEvent event) {
                return event.id() == 1L
                        ? Mono.error(new IllegalStateException("caído"))
                        : super.publish(event);
            }
        };
        var mixed = OutboxFixtures.handler(outbox, failingFirst, OutboxFixtures.properties(3, 1000, 200));

        StepVerifier.create(mixed.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(new PublishReport(2, 1, 1, false))
                .verifyComplete();
        assertThat(outbox.row(1).status()).isEqualTo("PENDING");
        assertThat(outbox.row(2).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void targetedRunPublishesOnlyTheGivenEvents() {
        long[] ids = addEvents(3);

        StepVerifier.create(handler.handle(PublishPendingEventsCommand.only(List.of(ids[1]))))
                .expectNext(new PublishReport(1, 1, 0, false))
                .verifyComplete();
        assertThat(publisher.confirmedIds()).containsExactly(ids[1]);
        assertThat(outbox.row(ids[0]).status()).isEqualTo("PENDING");
    }

    @Test
    void disabledPublisherClaimsNothing() {
        long id = addEvents(1)[0];
        var disabled = OutboxFixtures.handler(outbox, new FakeEventPublisher(false), OutboxFixtures.defaults());

        StepVerifier.create(disabled.handle(PublishPendingEventsCommand.due(50)))
                .expectNext(PublishReport.DISABLED)
                .verifyComplete();
        assertThat(outbox.row(id).claimed()).isFalse();
    }

    @Test
    void rejectsInvalidCommands() {
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(0)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_REQUEST)).verify();
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.due(501)))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_REQUEST)).verify();
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.only(List.of())))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_REQUEST)).verify();
        StepVerifier.create(handler.handle(PublishPendingEventsCommand.only(Arrays.asList(1L, null))))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_REQUEST)).verify();
        assertThat(publisher.attemptedIds()).isEmpty();
    }
}

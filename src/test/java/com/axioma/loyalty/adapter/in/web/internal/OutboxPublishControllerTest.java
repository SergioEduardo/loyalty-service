package com.axioma.loyalty.adapter.in.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.in.web.GlobalExceptionHandler;
import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.application.handler.PublishPendingEventsHandler;
import com.axioma.loyalty.domain.model.PublishReport;
import com.axioma.loyalty.support.OutboxFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class OutboxPublishControllerTest {

    private final PublishPendingEventsHandler handler = mock(PublishPendingEventsHandler.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new OutboxPublishController(handler, OutboxFixtures.defaults()))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void publishesABatchOfTheConfiguredSize() {
        when(handler.handle(any())).thenReturn(Mono.just(new PublishReport(3, 2, 1, false)));

        client.post().uri("/internal/outbox/publish").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"claimed\":3,\"published\":2,\"failed\":1}", JsonCompareMode.STRICT);

        var command = ArgumentCaptor.forClass(PublishPendingEventsCommand.class);
        verify(handler).handle(command.capture());
        assertThat(command.getValue()).isEqualTo(PublishPendingEventsCommand.due(50));
    }

    @Test
    void reportsDisabledWithoutATopic() {
        when(handler.handle(any())).thenReturn(Mono.just(PublishReport.DISABLED));

        client.post().uri("/internal/outbox/publish").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"claimed\":0,\"published\":0,\"failed\":0,\"disabled\":true}", JsonCompareMode.STRICT);
    }
}

package com.axioma.loyalty.adapter.in.web.internal;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.application.handler.PublishPendingEventsHandler;
import com.axioma.loyalty.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Red de seguridad del outbox (12.11): Cloud Scheduler la llama cada minuto con su identidad de
 * servicio (la valida InternalAuthFilter) y publica un lote de avisos pendientes.
 */
@RestController
@RequestMapping("/internal/outbox")
@RequiredArgsConstructor
public class OutboxPublishController {

    private final PublishPendingEventsHandler publishPendingEventsHandler;
    private final OutboxProperties properties;

    @PostMapping("/publish")
    public Mono<OutboxPublishResponse> publish() {
        return publishPendingEventsHandler.handle(PublishPendingEventsCommand.due(properties.batchSize()))
                .map(OutboxPublishResponse::from);
    }
}

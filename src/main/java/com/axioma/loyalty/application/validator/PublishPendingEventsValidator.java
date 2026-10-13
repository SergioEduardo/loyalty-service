package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.PublishPendingEventsCommand;
import com.axioma.loyalty.config.OutboxProperties;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PublishPendingEventsValidator {

    public Mono<Void> validate(PublishPendingEventsCommand command) {
        return Mono.fromRunnable(() -> check(command));
    }

    private static void check(PublishPendingEventsCommand command) {
        if (command.batchSize() < 1 || command.batchSize() > OutboxProperties.MAX_BATCH_SIZE) {
            throw new LoyaltyException(ErrorCode.INVALID_REQUEST);
        }
        if (command.targeted() && command.eventIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new LoyaltyException(ErrorCode.INVALID_REQUEST);
        }
    }
}

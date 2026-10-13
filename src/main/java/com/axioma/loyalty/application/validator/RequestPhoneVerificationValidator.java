package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.RequestPhoneVerificationCommand;
import com.axioma.loyalty.domain.model.PhoneE164;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** El teléfono se normaliza a E.164 (INVALID_PHONE). El slug lo resuelve el handler (TENANT_NOT_FOUND). */
@Component
public class RequestPhoneVerificationValidator {

    public Mono<PhoneE164> validate(RequestPhoneVerificationCommand command) {
        return Mono.fromCallable(() -> PhoneRules.normalize(command.phone()));
    }
}

package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.ActivateAccountCommand;
import com.axioma.loyalty.application.validator.ActivateAccountValidator;
import com.axioma.loyalty.domain.model.AuthSession;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * La persona elige su contraseña con el enlace y queda con sesión iniciada (misma respuesta que el
 * login). Orden: clave de firma lista, límite de intentos, enlace válido (si no, INVALID_ACTIVATION),
 * política de contraseña, bcrypt fuera de la transacción y luego AccountActivationWriter.
 */
@Service
@RequiredArgsConstructor
public class ActivateAccountHandler implements Handler<ActivateAccountCommand, Mono<AuthSession>> {

    private final TokenIssuer tokenIssuer;
    private final ActivationThrottle throttle;
    private final ActivationLookup lookup;
    private final ActivateAccountValidator validator;
    private final PasswordHasher passwordHasher;
    private final AccountActivationWriter writer;
    private final SessionStarter sessionStarter;

    @Override
    public Mono<AuthSession> handle(ActivateAccountCommand command) {
        var limits = throttle.limits(command.slug(), command.clientIp());
        return tokenIssuer.ensureReady()
                .then(throttle.ensureAllowed(limits))
                .then(Mono.defer(() -> lookup.findValid(command.slug(), command.token())))
                .switchIfEmpty(Mono.defer(() -> throttle.reject(limits)))
                .flatMap(target -> validator.validate(command)
                        .then(Mono.defer(() -> passwordHasher.hash(command.password())))
                        .flatMap(hash -> writer.activate(command.slug(), lookup.hash(command.token()), hash))
                        .switchIfEmpty(Mono.defer(() -> throttle.reject(limits)))
                        .flatMap(user -> throttle.clear(limits)
                                .then(Mono.defer(() -> sessionStarter.start(
                                        user, target.tenantSlug(), target.tenantName())))));
    }
}

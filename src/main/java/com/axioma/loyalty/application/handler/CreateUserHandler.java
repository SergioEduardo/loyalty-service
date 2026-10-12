package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.CreateUserCommand;
import com.axioma.loyalty.application.validator.CreateUserValidator;
import com.axioma.loyalty.domain.model.UserAccount;
import com.axioma.loyalty.domain.model.UserRole;
import com.axioma.loyalty.domain.port.out.PasswordHasher;
import com.axioma.loyalty.domain.port.out.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Una sola escritura: no necesita transacción y así el hash no retiene una conexión. */
@Service
@RequiredArgsConstructor
public class CreateUserHandler implements Handler<CreateUserCommand, Mono<UserAccount>> {

    private final CreateUserValidator validator;
    private final ActiveTenantGuard activeTenantGuard;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;

    @Override
    public Mono<UserAccount> handle(CreateUserCommand command) {
        return validator.validate(command)
                .then(Mono.defer(() -> activeTenantGuard.require(command.tenantId())))
                .then(Mono.defer(() -> passwordHasher.hash(command.password())))
                .flatMap(hash -> userRepository.create(new UserAccount(
                        UUID.randomUUID(),
                        command.tenantId(),
                        command.name(),
                        command.username(),
                        hash,
                        UserRole.valueOf(command.role()),
                        true)));
    }
}

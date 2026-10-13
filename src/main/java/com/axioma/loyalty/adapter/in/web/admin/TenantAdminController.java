package com.axioma.loyalty.adapter.in.web.admin;

import com.axioma.loyalty.application.command.DeactivateUserCommand;
import com.axioma.loyalty.application.command.IssueActivationLinkCommand;
import com.axioma.loyalty.application.command.ResetPasswordCommand;
import com.axioma.loyalty.application.handler.CreateTenantHandler;
import com.axioma.loyalty.application.handler.CreateUserHandler;
import com.axioma.loyalty.application.handler.DeactivateUserHandler;
import com.axioma.loyalty.application.handler.IssueActivationLinkHandler;
import com.axioma.loyalty.application.handler.ResetPasswordHandler;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * API de operador (la usa n8n) para dar de alta negocios y cuentas del personal. La protege
 * AdminAuthFilter. A diferencia del resto del servicio, el negocio viene de la URL.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final CreateTenantHandler createTenantHandler;
    private final CreateUserHandler createUserHandler;
    private final DeactivateUserHandler deactivateUserHandler;
    private final ResetPasswordHandler resetPasswordHandler;
    private final IssueActivationLinkHandler issueActivationLinkHandler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        return createTenantHandler.handle(request.toCommand()).map(TenantResponse::from);
    }

    @PostMapping("/{tenantId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> createUser(@PathVariable UUID tenantId, @RequestBody CreateUserRequest request) {
        return createUserHandler.handle(request.toCommand(tenantId)).map(UserResponse::from);
    }

    @PostMapping("/{tenantId}/users/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivateUser(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        return deactivateUserHandler.handle(new DeactivateUserCommand(tenantId, userId));
    }

    @PostMapping("/{tenantId}/users/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resetPassword(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                    @RequestBody ResetPasswordRequest request) {
        if (request == null) {
            return Mono.error(new LoyaltyException(ErrorCode.INVALID_REQUEST));
        }
        return resetPasswordHandler.handle(new ResetPasswordCommand(tenantId, userId, request.password()));
    }

    /** Enlace nuevo de activación (reenvío o cambio de contraseña por enlace); invalida los anteriores. */
    @PostMapping("/{tenantId}/users/{userId}/activation-link")
    public Mono<ActivationLinkResponse> issueActivationLink(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        return issueActivationLinkHandler.handle(new IssueActivationLinkCommand(tenantId, userId))
                .map(ActivationLinkResponse::from);
    }
}

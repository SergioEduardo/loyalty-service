package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.adapter.in.web.ClientIp;
import com.axioma.loyalty.application.command.ActivateAccountCommand;
import com.axioma.loyalty.application.command.LogoutCommand;
import com.axioma.loyalty.application.command.RefreshSessionCommand;
import com.axioma.loyalty.application.handler.ActivateAccountHandler;
import com.axioma.loyalty.application.handler.InspectActivationHandler;
import com.axioma.loyalty.application.handler.LoginHandler;
import com.axioma.loyalty.application.handler.LogoutHandler;
import com.axioma.loyalty.application.handler.RefreshSessionHandler;
import com.axioma.loyalty.application.query.InspectActivationQuery;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Sesiones del personal. login, refresh, logout y la activación por enlace son públicos; /me pasa
 * por StaffAuthFilter.
 * El negocio sale del token (o del slug al iniciar sesión), nunca de la URL.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginHandler loginHandler;
    private final RefreshSessionHandler refreshSessionHandler;
    private final LogoutHandler logoutHandler;
    private final InspectActivationHandler inspectActivationHandler;
    private final ActivateAccountHandler activateAccountHandler;

    @PostMapping("/login")
    public Mono<TokenResponse> login(@RequestBody LoginRequest request) {
        return loginHandler.handle(request.toCommand()).map(TokenResponse::from);
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return refreshSessionHandler.handle(new RefreshSessionCommand(request.refreshToken()))
                .map(TokenResponse::from);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        return logoutHandler.handle(new LogoutCommand(request == null ? null : request.refreshToken()));
    }

    /** A quién pertenece un enlace de activación. Cualquier fallo: 400 INVALID_ACTIVATION. */
    @PostMapping("/activation/inspect")
    public Mono<ActivationInfoResponse> inspectActivation(
            @RequestBody(required = false) ActivationInspectRequest request, ServerHttpRequest httpRequest) {
        var body = request == null ? new ActivationInspectRequest(null, null) : request;
        return inspectActivationHandler
                .handle(new InspectActivationQuery(body.slug(), body.token(), ClientIp.of(httpRequest)))
                .map(ActivationInfoResponse::from);
    }

    /** Elige la contraseña con el enlace y deja la sesión iniciada (misma respuesta que /login). */
    @PostMapping("/activate")
    public Mono<TokenResponse> activate(@RequestBody(required = false) ActivateRequest request,
                                        ServerHttpRequest httpRequest) {
        var body = request == null ? new ActivateRequest(null, null, null) : request;
        return activateAccountHandler.handle(new ActivateAccountCommand(
                        body.slug(), body.token(), body.password(), ClientIp.of(httpRequest)))
                .map(TokenResponse::from);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal) {
        return MeResponse.from(principal);
    }
}

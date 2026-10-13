package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.application.command.LogoutCommand;
import com.axioma.loyalty.application.command.RefreshSessionCommand;
import com.axioma.loyalty.application.handler.LoginHandler;
import com.axioma.loyalty.application.handler.LogoutHandler;
import com.axioma.loyalty.application.handler.RefreshSessionHandler;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Sesiones del personal. login, refresh y logout son públicos; /me pasa por StaffAuthFilter.
 * El negocio sale del token (o del slug al iniciar sesión), nunca de la URL.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginHandler loginHandler;
    private final RefreshSessionHandler refreshSessionHandler;
    private final LogoutHandler logoutHandler;

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

    @GetMapping("/me")
    public MeResponse me(@RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal) {
        return MeResponse.from(principal);
    }
}

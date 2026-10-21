package com.axioma.loyalty.adapter.in.web.enroll;

import com.axioma.loyalty.adapter.in.web.ClientIp;
import com.axioma.loyalty.application.command.EnrollCardCommand;
import com.axioma.loyalty.application.command.RequestPhoneVerificationCommand;
import com.axioma.loyalty.application.handler.EnrollCardHandler;
import com.axioma.loyalty.application.handler.RequestPhoneVerificationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Alta de la tarjeta del cliente (12.6 y 12.7). Público, como la activación: el negocio va por slug en
 * el cuerpo (no en la ruta, que sí se registra) y la IP sale de ClientIp. La página de alta lee la
 * identidad del negocio de GET /api/public/tenants/{slug}/identity.
 */
@RestController
@RequestMapping("/api/public/enroll")
@RequiredArgsConstructor
public class EnrollmentController {

    private final RequestPhoneVerificationHandler requestPhoneVerificationHandler;
    private final EnrollCardHandler enrollCardHandler;

    @PostMapping("/verification")
    public Mono<PhoneVerificationResponse> requestVerification(
            @RequestBody(required = false) PhoneVerificationRequest request, ServerHttpRequest httpRequest) {
        var body = request == null ? new PhoneVerificationRequest(null, null, null) : request;
        return requestPhoneVerificationHandler
                .handle(new RequestPhoneVerificationCommand(body.slug(), body.phone(), body.channel(),
                        ClientIp.of(httpRequest)))
                .map(PhoneVerificationResponse::from);
    }

    @PostMapping("/card")
    public Mono<EnrollCardResponse> enrollCard(@RequestBody(required = false) EnrollCardRequest request,
                                               ServerHttpRequest httpRequest) {
        var body = request == null ? new EnrollCardRequest(null, null, null, null, null) : request;
        return enrollCardHandler.handle(new EnrollCardCommand(body.slug(), body.phone(), body.name(),
                        body.verificationId(), body.code(), ClientIp.of(httpRequest)))
                .map(EnrollCardResponse::from);
    }
}

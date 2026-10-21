package com.axioma.loyalty.application.handler;

import com.axioma.loyalty.application.command.RequestPhoneVerificationCommand;
import com.axioma.loyalty.application.validator.RequestPhoneVerificationValidator;
import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.SentVerification;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationChannelPolicy;
import com.axioma.loyalty.domain.model.VerificationMessage;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.axioma.loyalty.domain.model.error.VerificationDeliveryException;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.VerificationCodeGenerator;
import com.axioma.loyalty.domain.port.out.VerificationCodeHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Pedir un código (12.6). Orden: teléfono a E.164 (INVALID_PHONE), sal y adaptador de envío listos
 * (VERIFICATION_UNAVAILABLE), canal pedido o el de por defecto (CHANNEL_NOT_AVAILABLE, sin gastar
 * límites), límite por IP, negocio activo por slug (TENANT_NOT_FOUND), límite por negocio, límite por
 * teléfono + alta de la verificación (solo el hash del código) y envío. Los límites, el vencimiento y
 * los intentos no dependen del canal. Nunca consulta tarjetas: la respuesta y el tiempo no dependen de
 * si el teléfono ya tiene una. Si el envío falla, la verificación se queda (cuenta para el límite) y
 * responde RECIPIENT_UNREACHABLE (el número no recibe por ese canal; con el canal intentado y los habilitados)
 * o VERIFICATION_UNAVAILABLE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestPhoneVerificationHandler
        implements Handler<RequestPhoneVerificationCommand, Mono<SentVerification>> {

    private final RequestPhoneVerificationValidator validator;
    private final VerificationCodeHasher hasher;
    private final PhoneVerificationSenderPort sender;
    private final VerificationChannelPolicy channels;
    private final EnrollRateLimits limits;
    private final ActiveTenantGuard tenantGuard;
    private final VerificationCodeGenerator codeGenerator;
    private final PhoneVerificationIssuer issuer;
    private final EnrollProperties properties;

    @Override
    public Mono<SentVerification> handle(RequestPhoneVerificationCommand command) {
        return validator.validate(command)
                .flatMap(phone -> hasher.ensureReady()
                        .then(sender.ensureReady())
                        .then(Mono.fromCallable(() -> channels.resolve(command.channel())))
                        .flatMap(channel -> limits.checkVerificationIp(command.clientIp())
                                .then(Mono.defer(() -> tenantGuard.requireBySlug(command.slug())))
                                .flatMap(tenant -> limits.checkVerificationTenant(tenant.id())
                                        .then(Mono.defer(() -> issueAndSend(tenant, phone, channel))))));
    }

    private Mono<SentVerification> issueAndSend(Tenant tenant, PhoneE164 phone, VerificationChannel channel) {
        String code = codeGenerator.generate();
        String codeHash = hasher.hash(tenant.id(), phone, code);
        return issuer.issue(tenant.id(), phone, codeHash, channel)
                .flatMap(issued -> sender
                        .send(new VerificationMessage(tenant.id(), tenant.name(), issued.id(), phone, code, channel,
                                properties.codeTtlMinutes()))
                        .onErrorMap(e -> {
                            // Sin el mensaje ni la causa: podrían traer el teléfono.
                            log.warn("Verification code not sent tenantId={} verificationId={} channel={} error={} providerCode={}",
                                    tenant.id(), issued.id(), channel.value(), e.getClass().getSimpleName(),
                                    e instanceof VerificationDeliveryException de && de.getProviderCode() != null
                                            ? de.getProviderCode() : "-");
                            if (e instanceof VerificationDeliveryException de
                                    && de.getCode() == ErrorCode.RECIPIENT_UNREACHABLE) {
                                return de.withChannels(channel, channels.enabled());
                            }
                            return e instanceof LoyaltyException le
                                    && (le.getCode() == ErrorCode.VERIFICATION_UNAVAILABLE
                                        || le.getCode() == ErrorCode.RECIPIENT_UNREACHABLE)
                                    ? e : new LoyaltyException(ErrorCode.VERIFICATION_UNAVAILABLE);
                        })
                        .then(Mono.fromCallable(() -> {
                            log.info("Verification code sent tenantId={} verificationId={} channel={}",
                                    tenant.id(), issued.id(), channel.value());
                            return new SentVerification(issued.id(), issued.expiresAt(), channel, channels.enabled());
                        })));
    }
}

package com.axioma.loyalty.config;

import com.axioma.loyalty.adapter.out.verification.DisabledPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.LogPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.WhatsAppPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.wallet.DisabledWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.GoogleServiceIdTokenSource;
import com.axioma.loyalty.adapter.out.wallet.HttpWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.ServiceIdTokenSource;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties({EnrollProperties.class, WalletProperties.class})
public class EnrollConfig {

    /**
     * Adaptador de envío según VERIFICATION_SENDER. "log" escribe el código en los logs: solo se
     * acepta con APP_ENV=dev; en cualquier otro caso la aplicación no arranca. Un valor desconocido
     * tampoco arranca.
     */
    @Bean
    public PhoneVerificationSenderPort phoneVerificationSender(EnrollProperties properties) {
        return switch (properties.verificationSender()) {
            case "disabled" -> {
                log.warn("VERIFICATION_SENDER=disabled: pedir un código responde VERIFICATION_UNAVAILABLE");
                yield new DisabledPhoneVerificationSender();
            }
            case "log" -> {
                if (!properties.devEnvironment()) {
                    throw new IllegalStateException(
                            "VERIFICATION_SENDER=log escribe los códigos en los logs: solo se permite con APP_ENV=dev");
                }
                log.warn("VERIFICATION_SENDER=log: los códigos de verificación se escriben en los logs (solo dev)");
                yield new LogPhoneVerificationSender();
            }
            case "whatsapp" -> {
                log.warn("VERIFICATION_SENDER=whatsapp: el adaptador aún no está implementado; responde VERIFICATION_UNAVAILABLE");
                yield new WhatsAppPhoneVerificationSender();
            }
            default -> throw new IllegalStateException("VERIFICATION_SENDER debe ser disabled, log o whatsapp");
        };
    }

    @Bean
    public ServiceIdTokenSource serviceIdTokenSource() {
        return new GoogleServiceIdTokenSource();
    }

    @Bean
    public WalletPassIssuerPort walletPassIssuer(WalletProperties properties, ServiceIdTokenSource idTokens,
                                                 WebClient.Builder webClientBuilder) {
        if (!properties.configured()) {
            log.warn("WALLET_SERVICE_URL vacía: el alta de tarjetas responde PASS_SERVICE_UNAVAILABLE");
            return new DisabledWalletPassIssuer();
        }
        int connectTimeout = (int) Math.min(properties.issueTimeoutMs(), 5000L);
        HttpClient http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        WebClient client = webClientBuilder.clone()
                .baseUrl(properties.serviceUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
        return new HttpWalletPassIssuer(client, idTokens, properties);
    }
}

package com.axioma.loyalty.config;

import com.axioma.loyalty.adapter.out.verification.ChannelRoutingPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.DisabledPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.LogPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.TwilioSmsPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.verification.WhatsAppPhoneVerificationSender;
import com.axioma.loyalty.adapter.out.wallet.DisabledWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.GoogleServiceIdTokenSource;
import com.axioma.loyalty.adapter.out.wallet.HttpWalletPassIssuer;
import com.axioma.loyalty.adapter.out.wallet.ServiceIdTokenSource;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationChannelPolicy;
import com.axioma.loyalty.domain.port.out.PhoneVerificationSenderPort;
import com.axioma.loyalty.domain.port.out.WalletPassIssuerPort;
import io.netty.channel.ChannelOption;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties({EnrollProperties.class, WalletProperties.class, WhatsAppProperties.class,
        TwilioProperties.class})
public class EnrollConfig {

    /**
     * Canal por defecto y canales disponibles. VERIFICATION_SENDER: disabled (sin canales), log (solo con
     * APP_ENV=dev; simula los canales de VERIFICATION_ENABLED_CHANNELS, o todos si está vacía, y el primero
     * es el de por defecto), whatsapp o sms (canal por defecto; VERIFICATION_ENABLED_CHANNELS vacía = solo
     * ese). No arranca con un canal desconocido, con el canal por defecto fuera de la lista o con log fuera
     * de dev.
     */
    @Bean
    public VerificationChannelPolicy verificationChannelPolicy(EnrollProperties properties) {
        List<VerificationChannel> listed = enabledChannels(properties.verificationEnabledChannels());
        String sender = properties.verificationSender();
        switch (sender) {
            case "disabled" -> {
                log.warn("VERIFICATION_SENDER=disabled: pedir un código responde VERIFICATION_UNAVAILABLE");
                if (!listed.isEmpty()) {
                    log.warn("VERIFICATION_ENABLED_CHANNELS se ignora con VERIFICATION_SENDER=disabled");
                }
                return VerificationChannelPolicy.none();
            }
            case "log" -> {
                if (!properties.devEnvironment()) {
                    throw new IllegalStateException(
                            "VERIFICATION_SENDER=log escribe los códigos en los logs: solo se permite con APP_ENV=dev");
                }
                List<VerificationChannel> enabled = listed.isEmpty() ? List.of(VerificationChannel.values()) : listed;
                return new VerificationChannelPolicy(enabled.get(0), enabled);
            }
            default -> {
                VerificationChannel defaultChannel = VerificationChannel.parse(sender)
                        .orElseThrow(() -> new IllegalStateException(
                                "VERIFICATION_SENDER debe ser disabled, log, whatsapp o sms"));
                List<VerificationChannel> enabled = listed.isEmpty() ? List.of(defaultChannel) : listed;
                if (!enabled.contains(defaultChannel)) {
                    throw new IllegalStateException("VERIFICATION_SENDER=" + sender
                            + " debe estar en VERIFICATION_ENABLED_CHANNELS");
                }
                return new VerificationChannelPolicy(defaultChannel, enabled);
            }
        }
    }

    /**
     * Adaptador de envío. disabled: VERIFICATION_UNAVAILABLE. log: escribe el código en los logs (solo dev,
     * lo garantiza verificationChannelPolicy) en lugar de todos los canales. whatsapp/sms: un adaptador real
     * por canal habilitado; si a un canal habilitado le faltan credenciales, la aplicación no arranca.
     */
    @Bean
    public PhoneVerificationSenderPort phoneVerificationSender(EnrollProperties properties,
                                                               VerificationChannelPolicy channels,
                                                               WhatsAppProperties whatsApp, TwilioProperties twilio,
                                                               WebClient.Builder webClientBuilder) {
        switch (properties.verificationSender()) {
            case "disabled" -> {
                return new DisabledPhoneVerificationSender();
            }
            case "log" -> {
                log.warn("VERIFICATION_SENDER=log: los códigos de verificación se escriben en los logs (solo dev) channels={}",
                        names(channels.enabled()));
                return new LogPhoneVerificationSender();
            }
            default -> {
                Map<VerificationChannel, PhoneVerificationSenderPort> senders = new EnumMap<>(VerificationChannel.class);
                for (VerificationChannel channel : channels.enabled()) {
                    senders.put(channel, switch (channel) {
                        case WHATSAPP -> {
                            requireCredentials(channel, whatsApp.missing());
                            yield new WhatsAppPhoneVerificationSender(
                                    webClient(webClientBuilder, whatsApp.apiBaseUrl(), whatsApp.timeoutMs()), whatsApp);
                        }
                        case SMS -> {
                            requireCredentials(channel, twilio.missing());
                            yield new TwilioSmsPhoneVerificationSender(
                                    webClient(webClientBuilder, twilio.apiBaseUrl(), twilio.timeoutMs()), twilio);
                        }
                    });
                }
                log.info("Verification channels enabled={} default={}", names(channels.enabled()),
                        channels.defaultChannel().value());
                return new ChannelRoutingPhoneVerificationSender(senders);
            }
        }
    }

    /** whatsapp,sms -> [WHATSAPP, SMS] en ese orden, sin repetidos. Un nombre desconocido no arranca. */
    static List<VerificationChannel> enabledChannels(String raw) {
        Set<VerificationChannel> channels = new LinkedHashSet<>();
        for (String name : raw.split(",")) {
            if (name.isBlank()) {
                continue;
            }
            channels.add(VerificationChannel.parse(name).orElseThrow(() -> new IllegalStateException(
                    "VERIFICATION_ENABLED_CHANNELS solo admite whatsapp y sms, separados por comas")));
        }
        return List.copyOf(channels);
    }

    private static void requireCredentials(VerificationChannel channel, List<String> missing) {
        if (!missing.isEmpty()) {
            throw new IllegalStateException("El canal " + channel.value()
                    + " está habilitado pero faltan sus variables: " + String.join(", ", missing));
        }
    }

    private static String names(List<VerificationChannel> channels) {
        return channels.stream().map(VerificationChannel::value).collect(Collectors.joining(","));
    }

    /** Conexión con tope de min(timeout, 3 s); el tope de cada intento lo pone el adaptador. */
    private static WebClient webClient(WebClient.Builder builder, String baseUrl, long timeoutMs) {
        int connectTimeout = (int) Math.min(timeoutMs, 3000L);
        HttpClient http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
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

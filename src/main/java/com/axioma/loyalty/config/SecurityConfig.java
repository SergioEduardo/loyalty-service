package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, AdminProperties.class})
public class SecurityConfig {

    public static final String GOOGLE_ISSUER = "https://accounts.google.com";
    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    public BCryptPasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    /** Hilos dedicados al hash de contraseñas; acotados para que un pico no agote la CPU. */
    @Bean(destroyMethod = "dispose")
    public Scheduler passwordHashScheduler() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        return Schedulers.newBoundedElastic(threads, 100, "password-hash");
    }

    /**
     * ID tokens de Google: firma RS256 con las llaves públicas de Google, vigencia e issuer.
     * La audiencia y el correo los revisa AdminTokenVerifier.
     */
    @Bean
    public ReactiveJwtDecoder googleIdTokenDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER)));
        return decoder;
    }
}

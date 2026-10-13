package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableConfigurationProperties(LogoStorageProperties.class)
public class LogoConfig {

    /**
     * Hilos para decodificar y re-codificar logos. Pocos a propósito: cada imagen ocupa hasta
     * ~16 MB mientras se procesa y la instancia tiene 512 MB. Las demás esperan en cola.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler imageProcessingScheduler() {
        return Schedulers.newBoundedElastic(2, 50, "image-processing");
    }
}

package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.database")
public record DatabaseProperties(
        String url,
        @DefaultValue("10") int poolMaxSize,
        @DefaultValue("true") boolean migrateOnStartup) {
}

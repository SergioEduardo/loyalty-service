package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("8") int passwordMinLength,
        @DefaultValue("12") int bcryptStrength) {
}

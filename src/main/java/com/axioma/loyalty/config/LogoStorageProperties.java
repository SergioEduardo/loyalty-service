package com.axioma.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bucket de logos (lo crea Terraform: LOGOS_BUCKET) y la base de sus URLs públicas. Sin base
 * explícita se usa https://storage.googleapis.com/{bucket}.
 */
@ConfigurationProperties(prefix = "app.logos")
public record LogoStorageProperties(String bucket, String publicBaseUrl) {

    public LogoStorageProperties {
        bucket = bucket == null ? "" : bucket.strip();
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "https://storage.googleapis.com/" + bucket
                : publicBaseUrl.strip().replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return !bucket.isEmpty();
    }

    public String publicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }
}

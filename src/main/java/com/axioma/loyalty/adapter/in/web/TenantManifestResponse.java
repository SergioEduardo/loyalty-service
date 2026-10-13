package com.axioma.loyalty.adapter.in.web;

import com.axioma.loyalty.domain.model.TenantManifest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Web App Manifest (W3C): los nombres de campo van en snake_case. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantManifestResponse(
        String id,
        String name,
        @JsonProperty("short_name") String shortName,
        String description,
        String lang,
        @JsonProperty("start_url") String startUrl,
        String scope,
        String display,
        String orientation,
        @JsonProperty("background_color") String backgroundColor,
        @JsonProperty("theme_color") String themeColor,
        List<Icon> icons) {

    public record Icon(String src, String sizes, String type, String purpose) {
    }

    static TenantManifestResponse from(TenantManifest manifest) {
        return new TenantManifestResponse(
                manifest.id(),
                manifest.name(),
                manifest.shortName(),
                manifest.description(),
                manifest.lang(),
                manifest.startUrl(),
                manifest.scope(),
                manifest.display(),
                manifest.orientation(),
                manifest.backgroundColor(),
                manifest.themeColor(),
                manifest.icons().stream()
                        .map(icon -> new Icon(icon.src(), icon.sizes(), icon.type(), icon.purpose()))
                        .toList());
    }
}

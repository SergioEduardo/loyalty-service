package com.axioma.loyalty.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Cuerpo de todos los errores: un código estable y un mensaje para la persona. channel y availableChannels
 * solo los llevan los errores de envío del código que se resuelven con otro canal (ChannelAlternatives);
 * los demás errores salen solo con code y message.
 */
public record ApiError(String code, String message,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String channel,
                       @JsonInclude(JsonInclude.Include.NON_NULL) List<String> availableChannels) {

    public ApiError(String code, String message) {
        this(code, message, null, null);
    }
}

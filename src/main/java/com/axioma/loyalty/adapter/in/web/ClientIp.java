package com.axioma.loyalty.adapter.in.web;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * IP del cliente para limitar intentos. Detrás de Firebase Hosting y Cloud Run llega en el primer
 * valor de X-Forwarded-For; sin él, la dirección remota. El cliente puede falsear ese encabezado,
 * así que solo sirve de límite adicional: el límite por slug no depende de la IP.
 */
public final class ClientIp {

    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIp() {
    }

    /** null si no se conoce o no tiene forma de IP. */
    public static String of(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return valid(forwarded.split(",", 2)[0].strip());
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return null;
        }
        return valid(remote.getAddress().getHostAddress());
    }

    private static String valid(String candidate) {
        return candidate != null && IP.matcher(candidate).matches() ? candidate : null;
    }
}

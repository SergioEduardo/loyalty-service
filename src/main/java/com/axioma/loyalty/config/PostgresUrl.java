package com.axioma.loyalty.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Cadena de conexión postgresql://usuario:clave@host:puerto/base?sslmode=require
 * (el formato que entrega Neon) separada en partes para R2DBC y JDBC.
 * Los mensajes de error y toString nunca incluyen la cadena ni la contraseña.
 */
public record PostgresUrl(
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean sslRequired) {

    public static PostgresUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL está vacía");
        }
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("DATABASE_URL no tiene un formato válido");
        }
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL debe empezar con postgresql://");
        }
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new IllegalArgumentException("DATABASE_URL debe incluir usuario y contraseña");
        }
        int separator = userInfo.indexOf(':');
        String username = decode(userInfo.substring(0, separator));
        String password = decode(userInfo.substring(separator + 1));
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL debe incluir el host");
        }
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("DATABASE_URL debe incluir el nombre de la base");
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        boolean ssl = !"disable".equalsIgnoreCase(queryParam(uri.getRawQuery(), "sslmode"));
        return new PostgresUrl(uri.getHost(), port, path.substring(1), username, password, ssl);
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?sslmode=" + (sslRequired ? "require" : "disable");
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "PostgresUrl[host=" + host + ", port=" + port + ", database=" + database + "]";
    }
}

package com.axioma.loyalty.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Cadena de conexión postgresql://usuario:clave@host:puerto/base?sslmode=require
 * (el formato que entrega Neon) separada en partes para R2DBC y JDBC. Tolera que el secreto se
 * haya copiado como comando de Neon: psql 'postgresql://...'.
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
        String value = unwrap(raw);
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            // Sin la causa: su mensaje incluye la cadena completa.
            throw new IllegalArgumentException("DATABASE_URL no tiene un formato válido: debe ser "
                    + "postgresql://usuario:clave@host/base?sslmode=require (sin espacios; la clave "
                    + "con caracteres especiales debe ir codificada)");
        }
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL debe empezar con postgresql:// o postgres://");
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

    /** Quita el prefijo psql y las comillas que envuelven la cadena, si las hay. */
    private static String unwrap(String raw) {
        String value = raw.strip();
        if (value.regionMatches(true, 0, "psql", 0, 4)
                && value.length() > 4 && Character.isWhitespace(value.charAt(4))) {
            value = value.substring(4).strip();
        }
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' || first == '"') && first == last) {
                value = value.substring(1, value.length() - 1).strip();
            }
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("DATABASE_URL está vacía");
        }
        return value;
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

package com.axioma.loyalty.adapter.in.web;

import org.springframework.http.server.PathContainer;

/** Utilidades de ruta para los filtros de autenticación. */
public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * true si la ruta es el prefijo o está debajo de él. Compara con la ruta decodificada y sin
     * segmentos vacíos ni parámetros de matriz, igual que el ruteo de Spring, para que
     * /api/%61dmin o //api/admin no se salten un filtro.
     */
    public static boolean isUnder(PathContainer path, String prefix) {
        StringBuilder normalized = new StringBuilder();
        for (PathContainer.Element element : path.elements()) {
            if (element instanceof PathContainer.PathSegment segment && !segment.valueToMatch().isEmpty()) {
                normalized.append('/').append(segment.valueToMatch());
            }
        }
        String value = normalized.toString();
        return value.equals(prefix) || value.startsWith(prefix + "/");
    }
}

package com.axioma.loyalty.adapter.out.persist.query;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Carga el SQL externo (src/main/resources/sql/...) una sola vez, al iniciar la clase. */
final class SqlResource {

    private SqlResource() {
    }

    static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el SQL " + path, e);
        }
    }
}

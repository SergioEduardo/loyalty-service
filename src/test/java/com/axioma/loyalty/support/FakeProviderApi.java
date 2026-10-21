package com.axioma.loyalty.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Proveedor HTTP falso (WhatsApp Cloud API, Twilio) para las pruebas de contrato: guarda cada petición
 * (método, ruta, encabezados y cuerpo) y responde con el estado, el cuerpo JSON y el retraso elegidos.
 * No escribe logs.
 */
public final class FakeProviderApi implements AutoCloseable {

    /** headers con nombres en minúsculas (primer valor). */
    public record Received(String method, String path, Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private record Reply(int status, String body, long delayMillis) {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Reply reply = new Reply(200, "{}", 0);

    public FakeProviderApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new ConcurrentHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
            });
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(), headers,
                    new String(requestBody, StandardCharsets.UTF_8)));
            Reply current = reply;
            if (current.delayMillis() > 0) {
                try {
                    Thread.sleep(current.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(current.status(), body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
            } catch (IOException ignored) {
                // El cliente ya cortó (tiempo agotado).
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reply(int status, String body) {
        reply = new Reply(status, body, 0);
    }

    public void replySlowly(int status, String body, long delayMillis) {
        reply = new Reply(status, body, delayMillis);
    }

    public List<Received> received() {
        return received;
    }

    public void reset() {
        received.clear();
        reply = new Reply(200, "{}", 0);
    }

    /** URL de un puerto local sin servidor: la conexión se rechaza. */
    public static String refusedUrl() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

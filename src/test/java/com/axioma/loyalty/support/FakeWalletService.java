package com.axioma.loyalty.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * wallet-service falso para los IT: POST /internal/passes con el comportamiento elegido (mode) y las
 * peticiones recibidas (Authorization y cuerpo). No escribe logs.
 */
public final class FakeWalletService implements AutoCloseable {

    /** SLOW_ONCE: la primera petición tarda slowMillis (como un arranque en frío) y las demás responden al momento. */
    public enum Mode { OK, SLOW, SLOW_ONCE, DOWN, TEMPLATE_NOT_READY, UNAUTHORIZED }

    public record Received(String authorization, String body) {
    }

    private static final Pattern CARD_ID = Pattern.compile("\"cardId\"\\s*:\\s*\"([0-9a-f-]{36})\"");

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Mode mode = Mode.OK;
    private volatile long slowMillis = 3000;
    private final AtomicBoolean slowPending = new AtomicBoolean();

    public FakeWalletService() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/internal/passes", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"), body));
            int status;
            String response;
            switch (mode) {
                case SLOW -> {
                    sleep(slowMillis);
                    status = 200;
                    response = ok(body);
                }
                case SLOW_ONCE -> {
                    if (slowPending.compareAndSet(true, false)) {
                        sleep(slowMillis);
                    }
                    status = 200;
                    response = ok(body);
                }
                case DOWN -> {
                    status = 503;
                    response = "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"x\"}";
                }
                case TEMPLATE_NOT_READY -> {
                    status = 409;
                    response = "{\"code\":\"TEMPLATE_NOT_READY\",\"message\":\"x\"}";
                }
                case UNAUTHORIZED -> {
                    status = 401;
                    response = "{\"code\":\"UNAUTHORIZED\",\"message\":\"x\"}";
                }
                default -> {
                    status = 200;
                    response = ok(body);
                }
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // el cliente ya se fue (timeout)
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void mode(Mode mode) {
        slowPending.set(mode == Mode.SLOW_ONCE);
        this.mode = mode;
    }

    public void slowMillis(long millis) {
        this.slowMillis = millis;
    }

    public List<Received> received() {
        return received;
    }

    public static String linkFor(String cardId) {
        return "https://wallet.test/passes/" + cardId + ".pkpass?exp=1&sig=firma";
    }

    private static String ok(String body) {
        Matcher m = CARD_ID.matcher(body);
        String cardId = m.find() ? m.group(1) : "desconocida";
        String expires = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString();
        return "{\"cardId\":\"" + cardId + "\",\"isNew\":true,\"apple\":{\"url\":\"" + linkFor(cardId)
                + "\",\"expiresAt\":\"" + expires + "\"},\"google\":null}";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpServerExchange;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class UndertowPrivateApiHandler {

    private final PrivateApiHandler privateApiHandler;

    public UndertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        this.privateApiHandler = privateApiHandler;
    }

    public void handleRequest(HttpServerExchange exchange) {
        var path = exchange.getRequestPath() + "?" + exchange.getQueryString();

        exchange.dispatch(r -> Thread.ofVirtual().name("kora-undertow-private-api").start(r), _ -> {
            HttpServerResponse response;
            try {
                response = Objects.requireNonNull(this.privateApiHandler.handle(path));
            } catch (Exception e) {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send(Objects.requireNonNullElse(e.getMessage(), "Internal server error"), StandardCharsets.UTF_8);
                return;
            }
            exchange.setStatusCode(response.code());
            try (var body = response.body();) {
                if (body == null) {
                    exchange.endExchange();
                    return;
                }
                exchange.setResponseContentLength(body.contentLength());
                exchange.startBlocking();
                try (var os = exchange.getOutputStream()) {
                    body.write(os);
                }
            }
        });
    }
}

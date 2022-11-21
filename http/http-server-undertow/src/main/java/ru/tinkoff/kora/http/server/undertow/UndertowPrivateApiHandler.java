package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class UndertowPrivateApiHandler {
    private final PrivateApiHandler privateApiHandler;

    public UndertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        this.privateApiHandler = privateApiHandler;
    }

    public void handleRequest(HttpServerExchange exchange) {
        var path = exchange.getRequestPath() + "?" + exchange.getQueryString();

        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> Mono.from(this.privateApiHandler.handle(path))
            .subscribe(response -> {
                exchange.setStatusCode(response.code());
                exchange.setResponseContentLength(response.contentLength());
                Flux.from(response.body())
                    .collectList()
                    .subscribe(body -> {
                        var arr = body.toArray(ByteBuffer[]::new);
                        exchange.getResponseSender().send(arr);
                    }, error -> {
                        exchange.setStatusCode(500);
                        exchange.getResponseSender().send(error.getMessage(), StandardCharsets.UTF_8);
                    });
            }, error -> {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send(error.getMessage(), StandardCharsets.UTF_8);
            }));
    }
}

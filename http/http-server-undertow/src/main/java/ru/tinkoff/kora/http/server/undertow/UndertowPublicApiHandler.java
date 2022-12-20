package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class UndertowPublicApiHandler implements HttpHandler {
    private final PublicApiHandler publicApiHandler;
    @Nullable
    private final HttpServerTracer tracer;

    public UndertowPublicApiHandler(PublicApiHandler publicApiHandler, @Nullable HttpServerTracer tracer) {
        this.publicApiHandler = publicApiHandler;
        this.tracer = tracer;
    }

    public int handlersSize() {
        return this.publicApiHandler.handlersSize();
    }

    private static Map<String, List<String>> queryParams(HttpServerExchange httpServerExchange) {
        var undertowQueryParams = httpServerExchange.getQueryParameters();
        var queryParams = new HashMap<String, List<String>>(undertowQueryParams.size());
        for (var entry : undertowQueryParams.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue().stream()
                .filter(Predicate.not(String::isEmpty))
                .toList();
            queryParams.put(key, List.copyOf(value));
        }
        return Map.copyOf(queryParams);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var method = exchange.getRequestMethod().toString();
        var path = exchange.getRelativePath();
        var host = exchange.getHostName();
        var scheme = exchange.getRequestScheme();
        var headers = new UndertowHttpHeaders(exchange.getRequestHeaders());
        var queryParams = queryParams(exchange);
        var requestReceiver = exchange.getRequestReceiver();
        requestReceiver.pause();
        var body = Flux.<ByteBuffer>create(sink -> {
            var demand = new AtomicLong(0);
            record FirstItem(byte[] data, boolean last){}
            var firstItem = new AtomicReference<FirstItem>();
            requestReceiver.receivePartialBytes((ex, message, last) -> {
                requestReceiver.pause();
                ex.dispatch(SameThreadExecutor.INSTANCE, () -> {
                    var newDemand = demand.decrementAndGet();
                    if (newDemand < 0) {
                        // when we call receivePartialBytes for the first time it will send already received data to the callback even if receiver been paused
                        // so we will just keep it here until demand will grow
                        firstItem.set(new FirstItem(message, last));
                        return;
                    }
                    if (newDemand > 0) {
                        requestReceiver.resume();
                    }
                    if (message.length > 0) {
                        sink.next(ByteBuffer.wrap(message));
                    }
                    if (last) {
                        sink.complete();
                    }
                });
            }, (ex, e) -> sink.error(e));
            sink.onRequest(demandGrow -> {
                // this way demand growth will be executed on connection thread and and we won't have dispatch/resume race
                exchange.getConnection().getWorker().execute(() -> {
                    var oldDemand = demand.getAndAdd(demandGrow);
                    if (oldDemand == -1) {
                        var firstItemData = firstItem.getAndSet(null);
                        try {
                            sink.next(ByteBuffer.wrap(firstItemData.data));
                        } finally {
                            if (firstItemData.last) {
                                sink.complete();
                            }
                        }
                    }
                    if (oldDemand <= 0 && (oldDemand + demandGrow) > 0) {
                        requestReceiver.resume();
                    }
                });
            });
        }, FluxSink.OverflowStrategy.ERROR);


        var context = Context.current();

        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
            context.inject();
            try {
                var routerRequest = new PublicApiHandler.PublicApiRequest(method, path, host, scheme, headers, queryParams, body);
                var responseSender = new UndertowHttpResponseSender(exchange, tracer);
                this.publicApiHandler.process(routerRequest, responseSender);
            } catch (Throwable exception) {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send(StandardCharsets.UTF_8.encode(exception.getMessage()));
            } finally {
                Context.clear();
            }
        });
        Context.clear();
    }
}

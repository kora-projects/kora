package ru.tinkoff.kora.http.server.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Flow;

public class UndertowPrivateApiHandler {

    private final PrivateApiHandler privateApiHandler;

    public UndertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        this.privateApiHandler = privateApiHandler;
    }

    public void handleRequest(HttpServerExchange exchange) {
        var path = exchange.getRequestPath() + "?" + exchange.getQueryString();

        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> this.privateApiHandler.handle(path)
            .whenCompleteAsync((response, error) -> {
                if (error != null) {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send(error.getMessage(), StandardCharsets.UTF_8);
                    return;
                }
                if (response == null) {
                    exchange.setStatusCode(500);
                    exchange.endExchange();
                    return;
                }
                exchange.setStatusCode(response.code());
                var body = response.body();
                if (body == null) {
                    exchange.endExchange();
                    return;
                }
                exchange.setResponseContentLength(body.contentLength());
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    exchange.getResponseSender().send(full, new IoCallback() {
                        @Override
                        public void onComplete(HttpServerExchange exchange, Sender sender) {
                            try {
                                body.close();
                            } catch (IOException e) {
                            }
                            exchange.endExchange();
                        }

                        @Override
                        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                            try {
                                body.close();
                            } catch (IOException e) {
                                exception.addSuppressed(e);
                            }
                            exchange.endExchange();
                        }
                    });
                    return;
                }
                body.subscribe(new Flow.Subscriber<ByteBuffer>() {
                    private final List<ByteBuffer> buf = Collections.synchronizedList(new ArrayList<>());

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        buf.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        exchange.setStatusCode(500);
                        exchange.getResponseSender().send(error.getMessage(), StandardCharsets.UTF_8);
                        try {
                            body.close();
                        } catch (IOException ignore) {
                        }
                    }

                    @Override
                    public void onComplete() {
                        var arr = buf.toArray(ByteBuffer[]::new);
                        exchange.getConnection().getWorker().execute(() -> exchange.getResponseSender().send(arr));
                        try {
                            body.close();
                        } catch (IOException ignore) {
                        }
                    }
                });

            }, exchange.getConnection().getWorker()));
    }
}

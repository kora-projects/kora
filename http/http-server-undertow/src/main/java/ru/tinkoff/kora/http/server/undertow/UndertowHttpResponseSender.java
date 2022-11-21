package ru.tinkoff.kora.http.server.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.DirectByteBufferDeallocator;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseSender;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class UndertowHttpResponseSender implements HttpServerResponseSender {
    private final HttpServerExchange exchange;
    @Nullable
    private final HttpServerTracer tracer;

    public UndertowHttpResponseSender(HttpServerExchange exchange, @Nullable HttpServerTracer tracer) {
        this.exchange = exchange;
        this.tracer = tracer;
    }

    @Override
    public Mono<SendResult> send(HttpServerResponse httpResponse) {
        var contentLength = httpResponse.contentLength();
        var contentType = httpResponse.contentType();
        var headers = httpResponse.headers();
        this.exchange.setStatusCode(httpResponse.code());
        if (this.tracer != null) this.tracer.inject(
            Context.current(),
            this.exchange.getResponseHeaders(),
            (carrier, key, value) -> carrier.add(HttpString.tryFromString(key), value)
        );

        if (contentLength >= 0) {
            this.exchange.setResponseContentLength(contentLength);
        }
        this.exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        this.exchange.getResponseHeaders().put(Headers.SERVER, "kora/undertow");
        for (var header : headers) {
            var key = header.getKey();
            if (key.equals("server")) {
                continue;
            }
            if (key.equals("content-type")) {
                continue;
            }
            if (key.equals("content-length")) {
                continue;
            }
            if (key.equals("transfer-encoding")) {
                continue;
            }
            this.exchange.getResponseHeaders().addAll(HttpString.tryFromString(key), header.getValue());
        }
        if (httpResponse.body() instanceof Callable<?> callable) {
            final ByteBuffer body;
            try {
                body = (ByteBuffer) callable.call();
            } catch (Exception e) {
                return Mono.just(new HttpServerResponseSender.ResponseBodyErrorBeforeCommit(e));
            }
            return this.sendBody(body);
        }
        return this.sendBody(httpResponse.body());
    }

    private Mono<SendResult> sendBody(Flux<? extends ByteBuffer> body) {
        return Mono.create(sink -> body.subscribe(new HttpResponseBodySubscriber(this.exchange, sink)));
    }

    private Mono<SendResult> sendBody(ByteBuffer body) {
        if (body == null || body.remaining() == 0) {
            this.exchange.setResponseContentLength(0);
            return Mono.create(sink -> {
                this.exchange.addExchangeCompleteListener((exchange, nextListener) -> {
                    sink.success(new HttpServerResponseSender.Success(exchange.getStatusCode()));
                    nextListener.proceed();
                });
                this.exchange.endExchange();
            });
        } else {
            this.exchange.setResponseContentLength(body.remaining());
            return Mono.create(sink -> this.exchange.getResponseSender().send(body, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
                        sink.success(new Success(exchange1.getStatusCode()));
                        nextListener.proceed();
                    });
                    exchange.endExchange();
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    exchange.getResponseSender().close();
                    sink.success(new HttpServerResponseSender.ConnectionError(exception));
                }
            }));
        }
    }

    private static class HttpResponseBodySubscriber implements Subscriber<ByteBuffer> {
        private final HttpServerExchange exchange;
        private final MonoSink<HttpServerResponseSender.SendResult> sink;
        private volatile Subscription subscription;
        private final AtomicInteger state = new AtomicInteger(0);

        private HttpResponseBodySubscriber(HttpServerExchange exchange, MonoSink<HttpServerResponseSender.SendResult> sink) {
            this.exchange = exchange;
            this.sink = sink;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
            this.subscription = s;
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            var newState = this.state.incrementAndGet();
            if ((newState & (0x1 << 24)) != 0) {
                // stream is already completed, should not happen
                DirectByteBufferDeallocator.free(byteBuffer);
                return;
            }
            this.exchange.getResponseSender().send(byteBuffer, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    var newState = HttpResponseBodySubscriber.this.state.decrementAndGet();
                    DirectByteBufferDeallocator.free(byteBuffer);
                    if ((newState & (0x1 << 24)) != 0) {
                        exchange.addExchangeCompleteListener((ex, nextListener) -> {
                            HttpResponseBodySubscriber.this.sink.success(new HttpServerResponseSender.Success(ex.getStatusCode()));
                            nextListener.proceed();
                        });
                        exchange.endExchange();
                    } else {
                        HttpResponseBodySubscriber.this.subscription.request(1);
                    }
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    DirectByteBufferDeallocator.free(byteBuffer);
                    HttpResponseBodySubscriber.this.subscription.cancel();
                    exchange.getResponseSender().close();
                    HttpResponseBodySubscriber.this.sink.success(new HttpServerResponseSender.ConnectionError(exception));
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            if (this.exchange.isResponseStarted()) {
                this.exchange.getResponseSender().close();
                HttpResponseBodySubscriber.this.sink.success(new HttpServerResponseSender.ResponseBodyError(t));
                this.exchange.endExchange();
            } else {
                HttpResponseBodySubscriber.this.sink.success(new HttpServerResponseSender.ResponseBodyErrorBeforeCommit(t));
            }
        }

        @Override
        public void onComplete() {
            var newState = this.state.updateAndGet(oldState -> oldState | (0x1 << 24));
            if (newState == (0x1 << 24)) {
                // no chunks if flight
                this.exchange.addExchangeCompleteListener((exchange, nextListener) -> {
                    this.sink.success(new HttpServerResponseSender.Success(this.exchange.getStatusCode()));
                    nextListener.proceed();
                });
                this.exchange.endExchange();
            }
        }
    }
}

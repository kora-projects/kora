package ru.tinkoff.kora.http.server.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.DirectByteBufferDeallocator;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.LazySingleSubscription;
import ru.tinkoff.kora.common.util.flow.SingleSubscription;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiResponse;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.http.server.undertow.request.UndertowPublicApiRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;

public class UndertowExchangeProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private static final Class<?> REACTOR_NON_BLOCKING;
    private static final Class<?> FAST_THREAD_LOCAL;

    static {
        Class<?> reactorNonBlocking = null;
        Class<?> fastThreadLocal = null;
        try {
            reactorNonBlocking = Thread.currentThread().getContextClassLoader().loadClass("reactor.core.scheduler.NonBlocking");
        } catch (ClassNotFoundException ignore) {
        }
        try {
            fastThreadLocal = Thread.currentThread().getContextClassLoader().loadClass("io.netty.util.concurrent.FastThreadLocalThread");
        } catch (ClassNotFoundException ignore) {
        }

        REACTOR_NON_BLOCKING = reactorNonBlocking;
        FAST_THREAD_LOCAL = fastThreadLocal;
    }

    private final HttpServerExchange exchange;
    private final PublicApiHandler publicApiHandler;
    private final Context context;
    @Nullable
    private final HttpServerTracer tracer;

    public UndertowExchangeProcessor(HttpServerExchange exchange, PublicApiHandler publicApiHandler, Context context, @Nullable HttpServerTracer tracer) {
        this.exchange = exchange;
        this.publicApiHandler = publicApiHandler;
        this.context = context;
        this.tracer = tracer;
    }

    @Override
    public void run() {
        var exchange = this.exchange;
        var context = this.context;
        UndertowContext.set(context, exchange);
        context.inject();
        try {
            var request = new UndertowPublicApiRequest(exchange, context);
            var response = this.publicApiHandler.process(context, request);
            if (response.response().isDone()) {
                try {
                    var httpResponse = response.response().join();
                    if (httpResponse == null) {
                        this.sendResponse(exchange, response, HttpServerResponse.of(500), new IllegalStateException("Illegal state: response future is empty"));
                    } else {
                        this.sendResponse(exchange, response, httpResponse, null);
                    }
                } catch (CompletionException e) {
                    this.sendException(response, Objects.requireNonNullElse(e.getCause(), e));
                } catch (Throwable e) {
                    this.sendException(response, e);
                }
                return;
            }
            response.response().whenComplete((httpServerResponse, throwable) -> {
                if (httpServerResponse != null) {
                    sendResponse(exchange, response, httpServerResponse, null);
                } else if (throwable instanceof CompletionException ce && ce.getCause() != null) {
                    sendException(response, ce.getCause());
                } else if (throwable != null) {
                    sendException(response, throwable);
                } else {
                    this.sendResponse(exchange, response, HttpServerResponse.of(500), new IllegalStateException("Illegal state: response future is empty"));
                }
            });
        } catch (Throwable exception) {
            log.warn("Error dropped", exception);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(StandardCharsets.UTF_8.encode(Objects.requireNonNullElse(exception.getMessage(), "no message")));
        } finally {
            UndertowContext.clear(context);
        }
    }

    private void sendResponse(HttpServerExchange exchange, PublicApiResponse response, HttpServerResponse httpResponse, @Nullable Throwable error) {
        var headers = httpResponse.headers();
        exchange.setStatusCode(httpResponse.code());
        var tracer = this.tracer;
        if (tracer != null) tracer.inject(
            this.context,
            exchange.getResponseHeaders(),
            (carrier, key, value) -> carrier.add(HttpString.tryFromString(key), value)
        );

        exchange.getResponseHeaders().put(Headers.SERVER, "kora/undertow");
        var body = httpResponse.body();
        if (body == null) {
            this.setHeaders(exchange.getResponseHeaders(), headers, null);
            exchange.addExchangeCompleteListener((e, nextListener) -> {
                response.closeSendResponseSuccess(e.getStatusCode(), httpResponse.headers(), error);
                nextListener.proceed();
            });
            exchange.endExchange();
            return;
        } else {
            this.setHeaders(exchange.getResponseHeaders(), headers, body.contentType());
        }

        var contentType = body.contentType();
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            this.sendFullBody(response, httpResponse, full, error);
            return;
        }

        var contentLength = body.contentLength();
        if (contentLength >= 0) {
            exchange.setResponseContentLength(contentLength);
        }
        if (this.isInBlockingThread()) {
            if (!exchange.isBlocking()) {
                exchange.startBlocking();
            }
            try (var os = exchange.getOutputStream()) {
                body.write(os);
            } catch (IOException e) {
                response.closeConnectionError(exchange.getStatusCode(), e);
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(500);
                    exchange.endExchange();
                }
                return;
            } catch (Exception e) {
                response.closeBodyError(exchange.getStatusCode(), e);
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(500);
                    exchange.endExchange();
                }
                return;
            }
            if (exchange.isComplete()) {
                response.closeSendResponseSuccess(exchange.getStatusCode(), httpResponse.headers(), error);
            }
        } else {
            sendStreamingBody(response, headers, body, error);
        }
    }

    private void setHeaders(HeaderMap responseHeaders, HttpHeaders headers, @Nullable String contentType) {
        for (var header : headers) {
            var key = header.getKey();
            if (key.equals("server")) {
                continue;
            }
            if (key.equals("content-type") && contentType != null) {
                continue;
            }
            if (key.equals("content-length")) {
                continue;
            }
            if (key.equals("transfer-encoding")) {
                continue;
            }
            responseHeaders.addAll(HttpString.tryFromString(key), header.getValue());
        }
    }


    private void sendFullBody(PublicApiResponse response, HttpServerResponse httpResponse, @Nullable ByteBuffer body, @Nullable Throwable error) {
        var exchange = this.exchange;
        var headers = httpResponse.headers();
        if (body == null || body.remaining() == 0) {
            exchange.setResponseContentLength(0);
            exchange.addExchangeCompleteListener((e, nextListener) -> {
                response.closeSendResponseSuccess(e.getStatusCode(), headers, error);
                nextListener.proceed();
            });
            exchange.endExchange();
        } else {
            exchange.setResponseContentLength(body.remaining());
            // io.undertow.io.DefaultIoCallback
            exchange.getResponseSender().send(body, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    sender.close(new IoCallback() {
                        @Override
                        public void onComplete(HttpServerExchange exchange, Sender sender) {
                            if (exchange.isComplete()) {
                                response.closeSendResponseSuccess(exchange.getStatusCode(), headers, error);
                            } else {
                                exchange.addExchangeCompleteListener((e, nextListener) -> {
                                    response.closeSendResponseSuccess(e.getStatusCode(), headers, error);
                                    nextListener.proceed();
                                });
                                exchange.endExchange();
                            }
                        }

                        @Override
                        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                            try {
                                exchange.endExchange();
                            } finally {
                                response.closeConnectionError(exchange.getStatusCode(), error == null ? error : exception);
                            }
                        }
                    });
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    try {
                        exchange.endExchange();
                    } finally {
                        IoUtils.safeClose(exchange.getConnection());
                        response.closeConnectionError(exchange.getStatusCode(), error == null ? error : exception);
                    }
                }
            });
        }
    }

    private void sendException(PublicApiResponse response, Throwable error) {
        if (!(error instanceof HttpServerResponse rs)) {
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(Objects.requireNonNullElse(error.getMessage(), "Unknown error"), StandardCharsets.UTF_8, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    response.closeSendResponseSuccess(500, null, error);
                    IoCallback.END_EXCHANGE.onComplete(exchange, sender);
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    error.addSuppressed(exception);
                    response.closeConnectionError(500, error);
                    IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                }
            });
            return;
        }
        exchange.setStatusCode(rs.code());
        var body = rs.body();
        if (body == null) {
            this.setHeaders(exchange.getRequestHeaders(), rs.headers(), null);
            exchange.addExchangeCompleteListener((exchange, nextListener) -> {
                response.closeSendResponseSuccess(exchange.getStatusCode(), rs.headers(), error);
                nextListener.proceed();
            });
            exchange.setResponseContentLength(0);
            exchange.endExchange();
            return;
        } else {
            this.setHeaders(exchange.getResponseHeaders(), rs.headers(), body.contentType());
        }

        var contentType = body.contentType();
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, body.contentType());
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            sendFullBody(response, rs, full, error);
            return;
        }

        if (this.isInBlockingThread()) {
            if (!exchange.isBlocking()) {
                exchange.startBlocking();
            }
            try (var os = exchange.getOutputStream()) {
                body.write(os);
            } catch (IOException e) {
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(500);
                } else {
                    try {
                        exchange.getConnection().close();
                    } catch (IOException ex) {
                        e.addSuppressed(ex);
                    }
                }
                response.closeBodyError(exchange.getStatusCode(), e);
                exchange.endExchange();
                return;
            }
            response.closeSendResponseSuccess(exchange.getStatusCode(), rs.headers(), error);
            return;
        }
        sendStreamingBody(response, HttpHeaders.empty(), body, error);
    }

    private boolean isInBlockingThread() {
        return !isInIoThread();
    }

    private boolean isInIoThread() {
        var t = Thread.currentThread();
        if (exchange.getIoThread() == t) {
            return true;
        }
        if (REACTOR_NON_BLOCKING != null && REACTOR_NON_BLOCKING.isInstance(t)) {
            return true;
        }
        if (FAST_THREAD_LOCAL != null && FAST_THREAD_LOCAL.isInstance(t)) {
            return true;
        }
        return false;
    }

    private void sendStreamingBody(PublicApiResponse response, HttpHeaders headers, HttpBodyOutput body, @Nullable Throwable error) {
        body.subscribe(new HttpResponseBodySubscriber(exchange, response, headers, error));
    }

    private static class HttpResponseBodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final HttpServerExchange exchange;
        private final PublicApiResponse response;
        private final HttpHeaders headers;
        private final Throwable error;
        private volatile Subscription subscription;
        private final AtomicInteger state = new AtomicInteger(0);

        private HttpResponseBodySubscriber(HttpServerExchange exchange, PublicApiResponse response, HttpHeaders headers, @Nullable Throwable error) {
            this.exchange = exchange;
            this.response = response;
            this.headers = headers;
            this.error = error;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            var newState = this.state.incrementAndGet();
            if ((newState & (0x1 << 24)) != 0) {
                // stream is already completed, should not happen
                DirectByteBufferDeallocator.free(byteBuffer);
                return;
            }

            if (subscription instanceof SingleSubscription<?> || subscription instanceof LazySingleSubscription<?>) {
                this.exchange.setResponseContentLength(byteBuffer.remaining());
                this.exchange.getResponseSender().send(byteBuffer, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        if (exchange.isComplete()) {
                            response.closeSendResponseSuccess(exchange.getStatusCode(), headers, error);
                        } else {
                            exchange.addExchangeCompleteListener((e, nextListener) -> {
                                response.closeSendResponseSuccess(e.getStatusCode(), headers, error);
                                nextListener.proceed();
                            });
                            exchange.endExchange();
                        }
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        HttpResponseBodySubscriber.this.response.closeBodyError(exchange.getStatusCode(), exception);
                    }
                });
                return;
            }

            this.exchange.getResponseSender().send(byteBuffer, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    var newState = HttpResponseBodySubscriber.this.state.decrementAndGet();
                    DirectByteBufferDeallocator.free(byteBuffer);
                    if ((newState & (0x1 << 24)) != 0) {
                        exchange.addExchangeCompleteListener((ex, nextListener) -> {
                            HttpResponseBodySubscriber.this.response.closeSendResponseSuccess(ex.getStatusCode(), headers, null);
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
                    HttpResponseBodySubscriber.this.response.closeConnectionError(exchange.getStatusCode(), error == null ? exception : error);
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            var exchange = this.exchange;
            if (exchange.isResponseStarted()) {
                exchange.getResponseSender().close();
                HttpResponseBodySubscriber.this.response.closeBodyError(exchange.getStatusCode(), error == null ? t : error);
                exchange.endExchange();
            } else {
                exchange.setStatusCode(500);
                exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send(t.getMessage());
                exchange.endExchange();
                HttpResponseBodySubscriber.this.response.closeBodyError(exchange.getStatusCode(), error == null ? t : error);
            }
        }

        @Override
        public void onComplete() {
            if (this.subscription instanceof SingleSubscription<?> || subscription instanceof LazySingleSubscription<?>) {
                return;
            }

            var newState = this.state.updateAndGet(oldState -> oldState | (0x1 << 24));
            if (newState == (0x1 << 24)) {
                // no chunks if flight
                this.exchange.addExchangeCompleteListener((exchange, nextListener) -> {
                    HttpResponseBodySubscriber.this.response.closeSendResponseSuccess(exchange.getStatusCode(), null, error);
                    nextListener.proceed();
                });
                this.exchange.endExchange();
            }
        }
    }

}

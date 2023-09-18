package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.common.util.flow.ErrorSubscription;
import ru.tinkoff.kora.common.util.flow.SingleSubscription;
import ru.tinkoff.kora.http.common.body.HttpInBody;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscriber;

public final class UndertowRequestHttpBody implements HttpInBody {
    private final Context context;
    private final HttpServerExchange exchange;
    private byte[] firstData;
    private byte[] secondData;

    public UndertowRequestHttpBody(Context context, HttpServerExchange exchange) {
        this.context = context;
        this.exchange = exchange;
    }

    public UndertowRequestHttpBody(Context context, HttpServerExchange exchange, byte[] firstData) {
        this.context = context;
        this.exchange = exchange;
        this.firstData = Objects.requireNonNull(firstData);
    }

    public UndertowRequestHttpBody(Context context, HttpServerExchange exchange, byte[] firstData, byte[] secondData) {
        this.context = context;
        this.exchange = exchange;
        this.firstData = Objects.requireNonNull(firstData);
        this.secondData = Objects.requireNonNull(secondData);
    }

    @Override
    public int contentLength() {
        var contentLengthStr = this.exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        return contentLengthStr == null ? -1 : Integer.parseInt(contentLengthStr);
    }

    @Nullable
    @Override
    public String contentType() {
        return this.exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> s) {
        var exchange = this.exchange;
        var firstData = this.firstData;
        if (firstData != null) {
            var secondData = this.secondData;
            var subscription = new UndertowRequestHttpBodySubscription2(s, exchange, firstData, secondData);
            s.onSubscribe(subscription);
            this.firstData = null;
            this.secondData = null;
            return;
        }
        try (var pooled = exchange.getConnection().getByteBufferPool().allocate()) {
            var buffer = pooled.getBuffer();
            buffer.clear();
            Connectors.resetRequestChannel(exchange);
            var channel = exchange.getRequestChannel();
            var res = channel.read(buffer);
            if (res == -1) {
                FlowUtils.<ByteBuffer>empty(context).subscribe(s);
                return;
            } else if (res == 0) {
                var subscription = new UndertowRequestHttpBodySubscription0(s, exchange);
                s.onSubscribe(subscription);
                return;
            }
            buffer.flip();
            firstData = new byte[buffer.remaining()];
            buffer.get(firstData);
            buffer.clear();

            res = channel.read(buffer);
            if (res == 0) {
                var subscription = new UndertowRequestHttpBodySubscription2(s, exchange, firstData, null);
                s.onSubscribe(subscription);
                return;
            }
            if (res < 0) {
                s.onSubscribe(new SingleSubscription<>(s, context, ByteBuffer.wrap(firstData)));
                return;
            }
            buffer.flip();
            var secondData = new byte[buffer.remaining()];
            buffer.get(firstData);
            var subscription = new UndertowRequestHttpBodySubscription2(s, exchange, firstData, secondData);
            s.onSubscribe(subscription);
            return;
        } catch (IOException e) {
            var subscription = new ErrorSubscription<>(s, context, e);
            s.onSubscribe(subscription);
        }
    }

    @Override
    public InputStream getInputStream() {
        if (this.exchange.isInIoThread()) {
            return null;
        }
        if (this.exchange.isBlocking()) {
            return this.exchange.getInputStream();
        }
        this.exchange.startBlocking();
        return this.exchange.getInputStream();
    }

    @Override
    public void close() {
        this.exchange.getRequestReceiver().receivePartialBytes((exchange, message, last) -> {
        });
        this.exchange.getRequestReceiver().resume();
    }

    @Override
    public CompletionStage<ByteBuffer> collectBuf() {
        var exchange = this.exchange;
        var future = new CompletableFuture<ByteBuffer>();
        exchange.getRequestReceiver().receiveFullBytes(
            (ex, message) -> future.complete(ByteBuffer.wrap(message)),
            (ex, error) -> future.completeExceptionally(error)
        );
        return future;
    }

    @Override
    public CompletionStage<byte[]> collectArray() {
        var exchange = this.exchange;
        var future = new CompletableFuture<byte[]>();
        exchange.getRequestReceiver().receiveFullBytes(
            (ex, message) -> future.complete(message),
            (ex, error) -> future.completeExceptionally(error)
        );
        return future;
    }
}

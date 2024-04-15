package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.SameThreadExecutor;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.common.util.flow.ErrorSubscription;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscriber;

public final class UndertowRequestHttpBody implements HttpBodyInput {
    private final Context context;
    private final HttpServerExchange exchange;

    @Nullable
    private Queue<byte[]> prefetchedData;

    public UndertowRequestHttpBody(Context context, HttpServerExchange exchange, @Nullable Queue<byte[]> prefetchedData) {
        this.context = context;
        this.exchange = exchange;
        this.prefetchedData = prefetchedData;
    }

    @Override
    public long contentLength() {
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

        var prefetched = this.prefetchedData;
        if (prefetched != null && !prefetched.isEmpty()) {
            var subscription = new UndertowRequestHttpBodySubscription(s, exchange, prefetched);
            this.prefetchedData = null;
            s.onSubscribe(subscription);
            return;
        }

        try (var pooled = exchange.getConnection().getByteBufferPool().allocate()) {
            var buffer = pooled.getBuffer();
            buffer.clear();
            Connectors.resetRequestChannel(exchange);
            var channel = exchange.getRequestChannel();
            Connectors.resetRequestChannel(exchange);

            prefetched = new LinkedList<>();

            var res = channel.read(buffer);
            if (res == -1) {
                FlowUtils.<ByteBuffer>empty(context).subscribe(s);
                return;
            }

            while (res > 0) {
                buffer.flip();
                var data = new byte[buffer.remaining()];
                buffer.get(data);
                buffer.clear();
                prefetched.add(data);
                res = channel.read(buffer);
            }

            var subscription = new UndertowRequestHttpBodySubscription(s, exchange, prefetched);
            s.onSubscribe(subscription);
        } catch (IOException e) {
            var subscription = new ErrorSubscription<>(s, context, e);
            s.onSubscribe(subscription);
        }
    }

    @Override
    public InputStream asInputStream() {
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
    public CompletionStage<ByteBuffer> asBufferStage() {
        var exchange = this.exchange;
        var future = new CompletableFuture<ByteBuffer>();
        exchange.getRequestReceiver().receiveFullBytes(
            (ex, message) -> {
                ex.dispatch(SameThreadExecutor.INSTANCE, () -> {
                    var prefetched = this.prefetchedData;
                    if (prefetched == null || prefetchedData.isEmpty()) {
                        future.complete(ByteBuffer.wrap(message));
                        return;
                    }
                    this.prefetchedData = null;

                    var result = buildArray(prefetched, message);
                    future.complete(ByteBuffer.wrap(result));
                });
            },
            (ex, error) -> ex.dispatch(SameThreadExecutor.INSTANCE, () -> future.completeExceptionally(error))
        );
        return future;
    }

    @Override
    public CompletionStage<byte[]> asArrayStage() {
        var exchange = this.exchange;
        var future = new CompletableFuture<byte[]>();
        exchange.getRequestReceiver().receiveFullBytes(
            (ex, message) -> {
                ex.dispatch(SameThreadExecutor.INSTANCE, () -> {
                    var prefetched = this.prefetchedData;
                    if (prefetched == null || prefetchedData.isEmpty()) {
                        future.complete(message);
                        return;
                    }
                    this.prefetchedData = null;

                    var result = buildArray(prefetched, message);
                    future.complete(result);
                });
            },
            (ex, error) -> ex.dispatch(SameThreadExecutor.INSTANCE, () -> future.completeExceptionally(error))
        );
        return future;
    }

    private byte[] buildArray(Queue<byte[]> prefetched, byte[] message) {
        var all = new ArrayList<byte[]>();
        var size = message.length;
        while (!prefetched.isEmpty()) {
            var data = prefetched.poll();
            all.add(data);
            size += data.length;
        }

        all.add(message);

        var result = new byte[size];
        var pos = 0;
        for (byte[] bytes : all) {
            System.arraycopy(bytes, 0, result, pos, bytes.length);
            pos += bytes.length;
        }
        return result;
    }
}

package ru.tinkoff.kora.http.client.async;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.async.response.EmptyAsyncHttpClientResponse;
import ru.tinkoff.kora.http.client.async.response.QueuePublisher;
import ru.tinkoff.kora.http.client.async.response.SingleBufferAsyncHttpClientResponse;
import ru.tinkoff.kora.http.client.async.response.StreamingAsyncHttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class MonoSinkStreamAsyncHandler implements AsyncHandler<Object> {
    private final CompletableFuture<HttpClientResponse> future;
    private final AtomicReference<RequestPhase> phase = new AtomicReference<>(RequestPhase.REQUESTED);
    private final Context context;
    private HttpResponseStatus responseStatus;
    private io.netty.handler.codec.http.HttpHeaders headers;
    private QueuePublisher<ByteBuffer> publisher;

    public MonoSinkStreamAsyncHandler(Context context, CompletableFuture<HttpClientResponse> future) {
        this.future = future;
        this.context = context;
    }

    @Override
    public State onStatusReceived(HttpResponseStatus responseStatus) {
        this.responseStatus = responseStatus;

        return State.CONTINUE;
    }

    @Override
    public State onHeadersReceived(io.netty.handler.codec.http.HttpHeaders headers) {
        this.headers = headers;

        return State.CONTINUE;
    }

    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.BODY_STREAM_RECEIVED)) {
            var oldContext = Context.current();
            try {
                this.context.inject();
                if (bodyPart.length() <= 0) {
                    if (bodyPart.isLast()) {
                        this.future.complete(new EmptyAsyncHttpClientResponse(this.responseStatus, this.headers));
                    }
                    return State.CONTINUE;
                }
                var buf = ByteBuffer.allocate(bodyPart.length());
                buf.put(bodyPart.getBodyByteBuffer());
                buf.rewind();
                if (bodyPart.isLast()) {
                    this.future.complete(new SingleBufferAsyncHttpClientResponse(context, this.responseStatus, this.headers, buf));
                    return State.CONTINUE;
                }
                var s = publisher = new QueuePublisher<ByteBuffer>();
                s.next(buf);
                this.future.complete(new StreamingAsyncHttpClientResponse(this.responseStatus, this.headers, s));
            } finally {
                oldContext.inject();
            }
        } else {
            if (bodyPart.length() > 0) {
                var buf = ByteBuffer.allocate(bodyPart.length());
                buf.put(bodyPart.getBodyByteBuffer());
                buf.rewind();
                this.publisher.next(buf);
            }
            if (bodyPart.isLast()) {
                this.publisher.complete();
            }
        }
        return State.CONTINUE;
    }

    @Override
    public void onThrowable(Throwable t) {
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.ERROR)) {
            var oldContext = Context.current();
            try {
                this.context.inject();
                this.future.completeExceptionally(t);
            } finally {
                oldContext.inject();
            }
        } else {
            this.publisher.error(t);
        }
    }

    @Override
    public Object onCompleted() {
        // onStream can be skipped so we have to send response here
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.BODY_STREAM_RECEIVED)) {
            var oldContext = Context.current();
            try {
                this.context.inject();
                this.future.complete(new EmptyAsyncHttpClientResponse(this.responseStatus, this.headers));
            } finally {
                oldContext.inject();
            }
        }
        return null;
    }

    private enum RequestPhase {
        REQUESTED, ERROR, BODY_STREAM_RECEIVED
    }
}

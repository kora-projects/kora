package ru.tinkoff.kora.http.client.async;

import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncHttpClientResponse implements HttpClientResponse {
    private final HttpResponseStatus responseStatus;
    private final io.netty.handler.codec.http.HttpHeaders headers;
    private final Publisher<HttpResponseBodyPart> body;
    private final AtomicReference<BodyState> bodyState = new AtomicReference<>(BodyState.NON_SUBSCRIBED);
    private final Context ctx;

    public AsyncHttpClientResponse(Context context, HttpResponseStatus responseStatus, io.netty.handler.codec.http.HttpHeaders headers, Publisher<HttpResponseBodyPart> body) {
        this.responseStatus = responseStatus;
        this.headers = headers;
        this.body = body;
        this.ctx = context;
    }

    @Override
    public int code() {
        return this.responseStatus.getStatusCode();
    }

    @Override
    public HttpHeaders headers() {
        return new AsyncHttpClientHeaders(this.headers);
    }

    @Override
    public Flux<ByteBuffer> body() {
        return Flux.<ByteBuffer>create(sink -> {
                if (!this.bodyState.compareAndSet(BodyState.NON_SUBSCRIBED, BodyState.SUBSCRIBED)) {
                    sink.error(new IOException("Body was already subscribed"));
                    return;
                }
                this.body.subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        sink.onRequest(s::request);
                        sink.onCancel(s::cancel);
                    }

                    @Override
                    public void onNext(HttpResponseBodyPart part) {
                        var oldContext = Context.current();
                        AsyncHttpClientResponse.this.bodyState.set(BodyState.BYTES_RECEIVED);
                        try {
                            Context.Reactor.current(sink.contextView()).inject();
                            sink.next(part.getBodyByteBuffer());
                        } finally {
                            oldContext.inject();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        var oldContext = Context.current();
                        AsyncHttpClientResponse.this.bodyState.set(BodyState.ERROR);
                        try {
                            Context.Reactor.current(sink.contextView()).inject();
                            sink.error(t);
                        } finally {
                            oldContext.inject();
                        }
                    }

                    @Override
                    public void onComplete() {
                        var oldContext = Context.current();
                        try {
                            Context.Reactor.current(sink.contextView()).inject();
                            if (AsyncHttpClientResponse.this.bodyState.compareAndSet(BodyState.SUBSCRIBED, BodyState.COMPLETED)) {
                                sink.next(ByteBuffer.allocate(0));
                            } else {
                                AsyncHttpClientResponse.this.bodyState.set(BodyState.COMPLETED);
                            }
                            sink.complete();
                        } finally {
                            oldContext.inject();
                        }
                    }
                });
            })
            .contextWrite(c -> Context.Reactor.inject(c, ctx));
    }

    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            if (this.bodyState.compareAndSet(BodyState.NON_SUBSCRIBED, BodyState.COMPLETED)) {
                Flux.from(this.body).subscribe();
            }
        });
    }

    private enum BodyState {
        NON_SUBSCRIBED, SUBSCRIBED, BYTES_RECEIVED, COMPLETED, ERROR
    }
}

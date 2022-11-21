package ru.tinkoff.kora.http.client.async;

import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.handler.StreamedAsyncHandler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.MonoSink;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.atomic.AtomicReference;

class MonoSinkStreamAsyncHandler implements StreamedAsyncHandler<Object> {
    private final MonoSink<HttpClientResponse> sink;
    private final AtomicReference<RequestPhase> phase = new AtomicReference<>(RequestPhase.REQUESTED);
    private final Context context;
    private volatile HttpResponseStatus responseStatus;
    private volatile io.netty.handler.codec.http.HttpHeaders headers;

    public MonoSinkStreamAsyncHandler(Context context, MonoSink<HttpClientResponse> sink) {
        this.sink = sink;
        this.context = context;
    }

    @Override
    public State onStream(Publisher<HttpResponseBodyPart> publisher) {
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.BODY_STREAM_RECEIVED)) {
            var oldContext = Context.current();
            try {
                Context.Reactor.current(sink.contextView()).inject();
                this.sink.success(new AsyncHttpClientResponse(this.context, this.responseStatus, this.headers, publisher));
            } finally {
                oldContext.inject();
            }
        }

        return State.CONTINUE;
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
        return State.CONTINUE;
    }

    @Override
    public void onThrowable(Throwable t) {
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.ERROR)) {
            var oldContext = Context.current();
            try {
                Context.Reactor.current(sink.contextView()).inject();
                this.sink.error(t);
            } finally {
                oldContext.inject();
            }
        }
    }

    @Override
    public Object onCompleted() {
        // onStream can be skipped so we have to send response here
        if (this.phase.compareAndSet(RequestPhase.REQUESTED, RequestPhase.BODY_STREAM_RECEIVED)) {
            var oldContext = Context.current();
            try {
                Context.Reactor.current(sink.contextView()).inject();
                this.sink.success(new AsyncHttpClientResponse(this.context, this.responseStatus, this.headers, Flux.empty()));
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

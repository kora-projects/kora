package ru.tinkoff.kora.http.client.jdk;

import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Function;

public class JdkHttpClientResponse implements HttpClientResponse {
    private final HttpResponse<Flow.Publisher<List<ByteBuffer>>> response;
    private final JdkHttpClientHeaders headers;

    public JdkHttpClientResponse(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
        this.response = response;
        this.headers = new JdkHttpClientHeaders(this.response.headers());
    }

    @Override
    public int code() {
        return this.response.statusCode();
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public Flux<ByteBuffer> body() {
        return JdkFlowAdapter.flowPublisherToFlux(this.response.body()).flatMapIterable(Function.identity());
    }

    @Override
    public Mono<Void> close() {
        return Mono.empty();
    }
}

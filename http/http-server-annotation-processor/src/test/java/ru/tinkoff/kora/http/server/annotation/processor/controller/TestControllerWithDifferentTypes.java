package ru.tinkoff.kora.http.server.annotation.processor.controller;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static ru.tinkoff.kora.http.common.HttpMethod.*;

@HttpController
public class TestControllerWithDifferentTypes {
    @HttpRoute(method = GET, path = "/")
    Mono<HttpServerResponse> getRoot() {
        var response = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), UTF_8.encode("Hello world"));

        return Mono.delay(Duration.ofMillis(1)).thenReturn(response);
    }

    @HttpRoute(method = GET, path = "/somePage")
    Mono<HttpServerResponse> getSomePage(HttpServerRequest httpServerRequest) {
        var response = new SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), UTF_8.encode("Hello world"));

        return Mono.delay(Duration.ofMillis(1)).thenReturn(response);
    }

    @HttpRoute(method = POST, path = "/someEntity")
    Mono<SomeEntity> postSomeEntityById(SomeEntity someEntity) {

        return Mono.delay(Duration.ofMillis(1)).thenReturn(someEntity);
    }

    @HttpRoute(method = PUT, path = "/someEntityBlocking")
    SomeEntity postSomeEntity(SomeEntity someEntity) {
        return someEntity;
    }

    @HttpRoute(method = PATCH, path = "/someComplexGenericEntity")
    SomeEntity postSomeEntities(List<List<Tuple2<List<String>, List<SomeEntity>>>> someEntity) {
        return someEntity.get(0).get(0).getT2().get(0);
    }

    @HttpRoute(method = DELETE, path = "/deleteByteArrayVoidResult")
    public void deleteByteArrayVoidResult(byte[] data) {

    }

    @HttpRoute(method = DELETE, path = "/deleteByteArrayMonoVoidResult")
    public Mono<Void> deleteByteArrayMonoVoidResult(byte[] data) {
        return Mono.empty();
    }

    @HttpRoute(method = GET, path = "/getWithPrimitiveInt")
    int getWithPrimitiveInt(int data) {
        return data;
    }

    @HttpRoute(method = OPTIONS, path = "/publisherVoid")
    Mono<Void> postSomeFilePublisherResult(byte[] data) {
        return Mono.empty();
    }

    @HttpRoute(method = POST, path = "/publisherByteBuffer")
    Mono<Void> postSomeFilePublisherResult(Publisher<ByteBuffer> data) {
        return Mono.empty();
    }


}

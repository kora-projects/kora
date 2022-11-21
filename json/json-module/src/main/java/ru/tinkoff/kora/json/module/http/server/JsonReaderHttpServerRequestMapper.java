package ru.tinkoff.kora.json.module.http.server;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;

public class JsonReaderHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final JsonReader<T> reader;

    public JsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Mono<T> apply(HttpServerRequest request) {
        return ReactorUtils.toByteArrayMono(request.body())
            .handle((bytes, sink) -> {
                try {
                    sink.next(this.reader.read(bytes));
                } catch (Exception e) {
                    var httpException = HttpServerResponseException.of(e, 400, e.getMessage());
                    sink.error(httpException);
                }
            });
    }
}

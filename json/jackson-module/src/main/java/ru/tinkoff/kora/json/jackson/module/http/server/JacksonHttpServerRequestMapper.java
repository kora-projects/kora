package ru.tinkoff.kora.json.jackson.module.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.lang.reflect.Type;

public class JacksonHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final ObjectReader objectMapper;

    public JacksonHttpServerRequestMapper(ObjectMapper objectMapper, Type type) {
        this.objectMapper = objectMapper.readerFor(objectMapper.constructType(type));
    }

    @Override
    public Mono<T> apply(HttpServerRequest request) {
        return ReactorUtils.toByteArrayMono(request.body())
            .handle((bytes, sink) -> {
                try {
                    sink.next(this.objectMapper.readValue(bytes));
                } catch (Exception e) {
                    var httpException = HttpServerResponseException.of(e, 400, e.getMessage());
                    sink.error(httpException);
                }
            });
    }
}

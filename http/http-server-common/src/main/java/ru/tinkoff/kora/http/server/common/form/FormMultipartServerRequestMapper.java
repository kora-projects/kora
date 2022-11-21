package ru.tinkoff.kora.http.server.common.form;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.form.FormMultipart;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

public final class FormMultipartServerRequestMapper implements HttpServerRequestMapper<FormMultipart> {
    @Override
    public Mono<FormMultipart> apply(HttpServerRequest request) {
        return MultipartReader.read(request)
            .collectList()
            .map(FormMultipart::new);
    }
}

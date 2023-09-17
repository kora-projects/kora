package ru.tinkoff.kora.http.server.common.form;

import ru.tinkoff.kora.http.common.form.FormMultipart;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.util.concurrent.CompletionStage;

public final class FormMultipartAsyncServerRequestMapper implements HttpServerRequestMapper<CompletionStage<FormMultipart>> {
    @Override
    public CompletionStage<FormMultipart> apply(HttpServerRequest request) {
        return MultipartReader.read(request)
            .thenApply(FormMultipart::new);
    }
}

package ru.tinkoff.kora.http.server.common.form;

import ru.tinkoff.kora.http.common.form.FormMultipart;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

public final class FormMultipartServerRequestMapper implements HttpServerRequestMapper<FormMultipart> {
    @Override
    public FormMultipart apply(HttpServerRequest request) throws Exception {
        var parts = MultipartReader.read(request);
        return new FormMultipart(parts);
    }
}

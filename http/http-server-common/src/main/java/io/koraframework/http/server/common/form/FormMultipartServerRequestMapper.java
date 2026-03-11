package io.koraframework.http.server.common.form;

import io.koraframework.http.common.form.FormMultipart;
import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.handler.HttpServerRequestMapper;

public final class FormMultipartServerRequestMapper implements HttpServerRequestMapper<FormMultipart> {
    @Override
    public FormMultipart apply(HttpServerRequest request) throws Exception {
        var parts = MultipartReader.read(request);
        return new FormMultipart(parts);
    }
}

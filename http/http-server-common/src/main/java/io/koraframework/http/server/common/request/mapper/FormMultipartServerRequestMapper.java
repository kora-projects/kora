package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.common.form.FormMultipart;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;
import io.koraframework.http.server.common.request.form.MultipartReaderUtils;

public final class FormMultipartServerRequestMapper implements HttpServerRequestMapper<FormMultipart> {
    @Override
    public FormMultipart apply(HttpServerRequest request) throws Exception {
        var parts = MultipartReaderUtils.read(request);
        return new FormMultipart(parts);
    }
}

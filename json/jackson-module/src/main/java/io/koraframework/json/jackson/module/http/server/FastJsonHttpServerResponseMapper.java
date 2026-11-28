package io.koraframework.json.jackson.module.http.server;

import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.writer.*;
import io.koraframework.application.graph.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.jackson.module.http.*;

public final class FastJsonHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final ObjectWriter<T> objectWriter;

    @SuppressWarnings("unchecked")
    public FastJsonHttpServerResponseMapper(TypeRef<T> typeRef) {
        this.objectWriter = (ObjectWriter<T>) JSONFactory.getDefaultObjectWriterProvider().getObjectWriter(typeRef);
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request, T result) {
        var body = new FastJsonHttpBodyOutput<>(objectWriter, result);
        return HttpServerResponse.of(200, body);
    }
}

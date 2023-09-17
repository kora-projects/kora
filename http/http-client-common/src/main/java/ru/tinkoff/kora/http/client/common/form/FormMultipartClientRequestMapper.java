package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.form.FormMultipart;

public final class FormMultipartClientRequestMapper implements HttpClientRequestMapper<FormMultipart> {
    @Override
    public HttpClientRequestBuilder apply(Context ctx, HttpClientRequestBuilder builder, FormMultipart value) {
        return MultipartWriter.write(builder, value.parts());
    }
}

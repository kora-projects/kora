package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;

public final class FormUrlEncodedClientRequestMapper implements HttpClientRequestMapper<FormUrlEncoded> {
    @Override
    public HttpClientRequestBuilder apply(Request<FormUrlEncoded> request) {
        var writer = new UrlEncodedWriter();
        for (var part : request.parameter()) {
            for (var value : part.values()) {
                writer.add(part.name(), value);
            }
        }
        return writer.write(request.builder());
    }
}

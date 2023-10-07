package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;

public final class FormUrlEncodedClientRequestMapper implements HttpClientRequestMapper<FormUrlEncoded> {
    @Override
    public HttpBodyOutput apply(Context ctx, FormUrlEncoded form) {
        var writer = new UrlEncodedWriter();
        for (var part : form) {
            for (var value : part.values()) {
                writer.add(part.name(), value);
            }
        }
        return writer.write();
    }
}

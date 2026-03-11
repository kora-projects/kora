package io.koraframework.http.client.common.form;

import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.form.FormUrlEncoded;

public final class FormUrlEncodedClientRequestMapper implements HttpClientRequestMapper<FormUrlEncoded> {
    @Override
    public HttpBodyOutput apply(FormUrlEncoded form) {
        var writer = new UrlEncodedWriter();
        for (var part : form) {
            for (var value : part.values()) {
                writer.add(part.name(), value);
            }
        }
        return writer.write();
    }
}

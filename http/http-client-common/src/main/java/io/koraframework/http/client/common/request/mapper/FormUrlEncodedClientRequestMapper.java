package io.koraframework.http.client.common.request.mapper;

import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.request.form.FormUrlEncodedWriter;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.form.FormUrlEncoded;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class FormUrlEncodedClientRequestMapper implements HttpClientRequestMapper<FormUrlEncoded> {
    @Override
    public HttpBodyOutput apply(FormUrlEncoded form) {
        try(var writer = new FormUrlEncodedWriter()) {
            for (var part : form) {
                for (var value : part.values()) {
                    writer.add(part.name(), value);
                }
            }
            return writer.write();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

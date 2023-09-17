package ru.tinkoff.kora.http.server.common.form;

import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

public final class FormUrlEncodedAsyncServerRequestMapper implements HttpServerRequestMapper<CompletionStage<FormUrlEncoded>> {
    @Override
    public CompletionStage<FormUrlEncoded> apply(HttpServerRequest request) {
        var contentType = request.headers().getFirst("content-type");
        if (contentType == null || !contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            var rs = HttpServerResponseException.of(415, "Expected content type: 'application/x-www-form-urlencoded'");
            try {
                request.body().close();
            } catch (IOException e) {
                rs.addSuppressed(e);
            }
            throw rs;
        }
        return request.body().collectArray().thenApply(bytes -> {
            var str = new String(bytes, StandardCharsets.UTF_8);
            var parts = FormUrlEncodedServerRequestMapper.read(str);
            return new FormUrlEncoded(parts);
        });
    }
}

package ru.tinkoff.kora.http.client.ok;

import jakarta.annotation.Nullable;
import okhttp3.ResponseBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;

public final class OkHttpResponseBody implements HttpBodyInput {
    private final ResponseBody body;

    public OkHttpResponseBody(ResponseBody body) {
        this.body = body;
    }

    @Override
    public long contentLength() {
        return body.contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        var ct = body.contentType();
        if (ct == null) {
            return null;
        }
        return ct.toString();
    }

    @Override
    public InputStream asInputStream() {
        return this.body.byteStream();
    }


    @Override
    public void close() throws IOException {
        this.body.close();
    }
}

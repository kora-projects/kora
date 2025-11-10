package ru.tinkoff.kora.http.client.common.telemetry.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public final class DefaultHttpClientTelemetryRequestBodyWrapper implements HttpBodyOutput {

    private final HttpClientRequest rq;
    private final HttpBodyOutput body;
    private final DefaultHttpClientLogger log;
    private final Charset charset;

    public DefaultHttpClientTelemetryRequestBodyWrapper(HttpClientRequest rq, HttpBodyOutput body, Charset charset, DefaultHttpClientLogger log) {
        this.rq = rq;
        this.body = body;
        this.charset = charset;
        this.log = log;
    }

    @Override
    public long contentLength() {
        return body.contentLength();
    }

    @Nullable
    @Override
    public String contentType() {
        return body.contentType();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        var baos = new ByteArrayOutputStream();
        body.write(baos);
        log.logRequest(rq, baos.toString(this.charset));
        baos.writeTo(os);
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}

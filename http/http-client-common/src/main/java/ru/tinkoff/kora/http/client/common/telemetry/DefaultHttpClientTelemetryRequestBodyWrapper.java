package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DefaultHttpClientTelemetryRequestBodyWrapper implements HttpBodyOutput {

    private final HttpBodyOutput body;
    private final Consumer<List<ByteBuffer>> onComplete;

    public DefaultHttpClientTelemetryRequestBodyWrapper(HttpBodyOutput body, Consumer<List<ByteBuffer>> onComplete) {
        this.body = body;
        this.onComplete = onComplete;
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
        // todo rewrite with custom byte array output stream
        var buf = baos.toByteArray();
        onComplete.accept(List.of(ByteBuffer.wrap(buf)));
        os.write(buf);
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}

package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class DefaultFullHttpBody implements HttpBodyInput, HttpBodyOutput {
    private final ByteBuffer data;
    private final String contentType;

    public DefaultFullHttpBody(ByteBuffer data, @Nullable String contentType) {
        this.data = data;
        this.contentType = contentType;
    }

    @Override
    public long contentLength() {
        return data.remaining();
    }

    @Nullable
    @Override
    public String contentType() {
        return this.contentType;
    }

    @Override
    public ByteBuffer getFullContentIfAvailable() {
        return this.data.slice();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        var data = this.data;
        if (data.hasArray()) {
            os.write(data.array(), data.arrayOffset(), data.remaining());
        } else {
            var buf = new byte[1024];
            data = data.slice();
            while (data.hasRemaining()) {
                var len = Math.min(data.remaining(), buf.length);
                data.get(buf);
                os.write(buf, 0, len);
            }
        }
    }

    @Override
    public InputStream asInputStream() {
        return new ByteBufferInputStream(this.data.slice());
    }

    @Override
    public void close() {

    }
}

package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class EmptyHttpBody implements HttpBodyInput, HttpBodyOutput {

    public final static EmptyHttpBody INSTANCE = new EmptyHttpBody();

    private static final byte[] emptyArray = new byte[0];
    private static final ByteBuffer emptyBuffer = ByteBuffer.wrap(emptyArray);

    private EmptyHttpBody() {}

    @Override
    public ByteBuffer getFullContentIfAvailable() {
        return emptyBuffer;
    }

    @Override
    public long contentLength() {
        return 0;
    }

    @Nullable
    @Override
    public String contentType() {
        return null;
    }

    @Override
    public void write(OutputStream os) throws IOException {
    }

    @Override
    public InputStream asInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public void close() {

    }
}

package ru.tinkoff.kora.s3.client.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.s3.client.model.S3Body;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

@ApiStatus.Experimental
public record ByteArrayS3Body(
    byte[] bytes,
    int offset,
    int len,
    @Nullable String contentType,
    @Nullable String encoding) implements S3Body {

    @Override
    public byte[] asBytes() {
        if (offset == 0 && len == bytes.length) {
            return bytes;
        }
        return Arrays.copyOfRange(bytes, offset, offset + len);
    }

    @Override
    public InputStream asInputStream() {
        return new ByteArrayInputStream(bytes, offset, len);
    }

    @Override
    public long size() {
        return this.len;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ByteArrayS3Body that) {
            return len == that.len
                && Objects.equals(encoding, that.encoding)
                && Objects.equals(contentType, that.contentType)
                && Arrays.equals(bytes, offset, offset + len, that.bytes, that.offset, that.offset + len)
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(len, contentType, encoding);
    }
}

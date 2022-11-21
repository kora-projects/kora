package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.SerializableString;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record RawJson(@Nonnull byte[] value) implements SerializableString {
    public RawJson(@Nonnull String value) {
        this(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getValue() {
        return new String(value, StandardCharsets.UTF_8);
    }

    @Override
    public int charLength() {
        return getValue().length();
    }

    @Override
    public char[] asQuotedChars() {
        return getValue().toCharArray();
    }

    @Override
    public byte[] asUnquotedUTF8() {
        return this.value;
    }

    @Override
    public byte[] asQuotedUTF8() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendQuotedUTF8(byte[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendQuoted(char[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendUnquotedUTF8(byte[] buffer, int offset) {
        final int length = value.length;
        if ((offset + length) > buffer.length) {
            return -1;
        }
        System.arraycopy(value, 0, buffer, offset, length);
        return length;
    }

    @Override
    public int appendUnquoted(char[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeQuotedUTF8(OutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeUnquotedUTF8(OutputStream out) throws IOException {
        out.write(value, 0, value.length);
        return value.length;
    }

    @Override
    public int putQuotedUTF8(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int putUnquotedUTF8(ByteBuffer buffer) throws IOException {
        final int length = value.length;
        if (length > buffer.remaining()) {
            return -1;
        }
        buffer.put(value, 0, length);
        return length;
    }
}

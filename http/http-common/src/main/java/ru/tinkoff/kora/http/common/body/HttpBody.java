package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpBody extends Closeable {

    long contentLength();

    @Nullable
    String contentType();

    @Nullable
    default ByteBuffer getFullContentIfAvailable() {
        return null;
    }

    static EmptyHttpBody empty() {
        return EmptyHttpBody.INSTANCE;
    }

    static DefaultFullHttpBody of(byte[] content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content), null);
    }

    static DefaultFullHttpBody of(ByteBuffer content) {
        return new DefaultFullHttpBody(Context.current(), content, null);
    }

    static DefaultFullHttpBody of(@Nullable String contentType, byte[] content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content), contentType);
    }

    static DefaultFullHttpBody of(@Nullable String contentType, ByteBuffer content) {
        return new DefaultFullHttpBody(Context.current(), content, contentType);
    }

    static DefaultFullHttpBody of(Context context, @Nullable String contentType, byte[] content) {
        return new DefaultFullHttpBody(context, ByteBuffer.wrap(content), contentType);
    }

    static DefaultFullHttpBody of(Context context, @Nullable String contentType, ByteBuffer content) {
        return new DefaultFullHttpBody(context, content, contentType);
    }

    static DefaultFullHttpBody octetStream(byte[] content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content), "application/octet-stream");
    }

    static DefaultFullHttpBody octetStream(ByteBuffer content) {
        return new DefaultFullHttpBody(Context.current(), content, "application/octet-stream");
    }

    static DefaultFullHttpBody octetStream(Context context, byte[] content) {
        return new DefaultFullHttpBody(context, ByteBuffer.wrap(content), "application/octet-stream");
    }

    static DefaultFullHttpBody octetStream(Context context, ByteBuffer content) {
        return new DefaultFullHttpBody(context, content, "application/octet-stream");
    }

    static DefaultFullHttpBody plaintext(String content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody plaintext(ByteBuffer content) {
        return new DefaultFullHttpBody(Context.current(), content.slice(), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody plaintext(Context ctx, String content) {
        return new DefaultFullHttpBody(ctx, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody plaintext(Context ctx, ByteBuffer content) {
        return new DefaultFullHttpBody(ctx, content.slice(), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody json(String content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "application/json");
    }

    static DefaultFullHttpBody json(byte[] content) {
        return new DefaultFullHttpBody(Context.current(), ByteBuffer.wrap(content), "application/json");
    }

    static DefaultFullHttpBody json(Context context, String content) {
        return new DefaultFullHttpBody(context, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "application/json");
    }

    static DefaultFullHttpBody json(Context context, byte[] content) {
        return new DefaultFullHttpBody(context, ByteBuffer.wrap(content), "application/json");
    }
}

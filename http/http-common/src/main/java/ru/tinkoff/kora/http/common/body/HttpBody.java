package ru.tinkoff.kora.http.common.body;

import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * <b>Русский</b>: Описывает базовое тело HTTP запроса/ответа
 * <hr>
 * <b>English</b>: Describes the basic HTTP request/response body
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpBody.of("application/octet-stream", new byte{ 0x0 })
 * }
 * </pre>
 */
public interface HttpBody extends Closeable {

    /**
     * @return <b>Русский</b>: Возвращает длину тела запроса
     * <hr>
     * <b>English</b>: Returns the length of the body of the request
     */
    long contentLength();

    /**
     * @return <b>Русский</b>: Возвращает тип тела запроса как значения HTTP заголовка
     * <hr>
     * <b>English</b>: Returns request body type as HTTP header values
     * <br>
     * <br>
     * Пример / Example: <pre>application/json</pre>
     */
    @Nullable
    String contentType();

    /**
     * @return <b>Русский</b>: Возвращает полное тело запроса если оно доступно, иначе <i>null</i>
     * <hr>
     * <b>English</b>: Returns the full body of the request if available, otherwise <i>null</i>
     */
    @Nullable
    default ByteBuffer getFullContentIfAvailable() {
        return null;
    }

    /**
     * @return <b>Русский</b>: Возвращает пустое HTTP тело
     * <hr>
     * <b>English</b>: Returns empty HTTP body
     */
    static EmptyHttpBody empty() {
        return EmptyHttpBody.INSTANCE;
    }

    static DefaultFullHttpBody of(byte[] content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content), null);
    }

    static DefaultFullHttpBody of(ByteBuffer content) {
        return new DefaultFullHttpBody(content, null);
    }

    static DefaultFullHttpBody of(@Nullable String contentType, byte[] content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content), contentType);
    }

    static DefaultFullHttpBody of(@Nullable String contentType, ByteBuffer content) {
        return new DefaultFullHttpBody(content, contentType);
    }

    static DefaultFullHttpBody octetStream(byte[] content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content), "application/octet-stream");
    }

    static DefaultFullHttpBody octetStream(ByteBuffer content) {
        return new DefaultFullHttpBody(content, "application/octet-stream");
    }

    static DefaultFullHttpBody plaintext(String content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody plaintext(ByteBuffer content) {
        return new DefaultFullHttpBody(content.slice(), "text/plain; charset=utf-8");
    }

    static DefaultFullHttpBody json(String content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), "application/json");
    }

    static DefaultFullHttpBody json(byte[] content) {
        return new DefaultFullHttpBody(ByteBuffer.wrap(content), "application/json");
    }
}

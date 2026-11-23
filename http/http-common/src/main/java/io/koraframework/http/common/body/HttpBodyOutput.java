package io.koraframework.http.common.body;


import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <b>Русский</b>: Описывает тело HTTP ответа
 * <hr>
 * <b>English</b>: Describes HTTP response body
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * HttpBody.of("application/octet-stream", new byte{ 0x0 })
 * }
 * </pre>
 */
public interface HttpBodyOutput extends HttpBody {

    static HttpBodyOutput of(String contentType, HttpBodyWriter writer) {
        return new StreamingHttpBodyOutput(contentType, -1, writer);
    }

    static HttpBodyOutput of(String contentType, long length, HttpBodyWriter writer) {
        return new StreamingHttpBodyOutput(contentType, length, writer);
    }

    static HttpBodyOutput of(String contentType, InputStream inputStream) {
        return new InputStreamHttpBodyOutput(contentType, -1, inputStream);
    }

    static HttpBodyOutput of(String contentType, long length, InputStream inputStream) {
        return new InputStreamHttpBodyOutput(contentType, length, inputStream);
    }

    static HttpBodyOutput octetStream(InputStream inputStream) {
        return new InputStreamHttpBodyOutput("application/octet-stream", -1, inputStream);
    }

    static HttpBodyOutput octetStream(long length, InputStream inputStream) {
        return new InputStreamHttpBodyOutput("application/octet-stream", length, inputStream);
    }

    static HttpBodyOutput octetStream(HttpBodyWriter writer) {
        return new StreamingHttpBodyOutput("application/octet-stream", -1, writer);
    }

    static HttpBodyOutput octetStream(long length, HttpBodyWriter writer) {
        return new StreamingHttpBodyOutput("application/octet-stream", length, writer);
    }

    interface HttpBodyWriter {
        void write(OutputStream os) throws IOException;
    }

    long contentLength();

    @Nullable
    String contentType();

    void write(OutputStream os) throws IOException;

    /**
     * <b>Русский</b>: {@code true}, если {@link #write(OutputStream)} можно вызвать только один раз (например, тело читает {@link InputStream}),
     * и транспорт не должен повторно отправлять такое тело при редиректе или повторе запроса
     * <hr>
     * <b>English</b>: {@code true} when {@link #write(OutputStream)} can be called only once (e.g. the body reads an {@link InputStream}),
     * so a transport must not resend it on a redirect or a retry
     */
    default boolean isOneShot() {
        return false;
    }
}

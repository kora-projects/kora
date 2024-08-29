package ru.tinkoff.kora.http.common.form;

import jakarta.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * <b>Русский</b>: Описывает тело HTTP запроса/ответа как форма с бинарными данными
 * <hr>
 * <b>English</b>: Describes the HTTP request/response body as a form with binary data
 * <br>
 * <br>
 * <a href="https://ru.wikipedia.org/wiki/Multipart/form-data">Описание Формы</a>
 */
public record FormMultipart(List<? extends FormPart> parts) {

    public static FormPart data(String name, String value) {
        return new FormPart.MultipartData(name, value);
    }

    public static FormPart file(String name, @Nullable String fileName, @Nullable String contentType, byte[] content) {
        return new FormPart.MultipartFile(name, fileName, contentType, content);
    }

    public static FormPart file(String name, @Nullable String fileName, @Nullable String contentType, Flow.Publisher<ByteBuffer> content) {
        return new FormPart.MultipartFileStream(name, fileName, contentType, content);
    }

    public sealed interface FormPart {
        String name();

        record MultipartFile(String name, @Nullable String fileName, @Nullable String contentType, byte[] content) implements FormPart {}

        record MultipartFileStream(String name, @Nullable String fileName, @Nullable String contentType, Flow.Publisher<ByteBuffer> content) implements FormPart {}

        record MultipartData(String name, String content) implements FormPart {}
    }
}

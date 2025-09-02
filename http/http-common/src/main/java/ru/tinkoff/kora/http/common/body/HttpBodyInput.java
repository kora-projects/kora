package ru.tinkoff.kora.http.common.body;

import java.io.InputStream;

/**
 * <b>Русский</b>: Описывает тело HTTP запроса
 * <hr>
 * <b>English</b>: Describes HTTP request body
 */
public interface HttpBodyInput extends HttpBody {
    InputStream asInputStream();
}

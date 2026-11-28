package io.koraframework.http.common.body;

import java.io.*;

/**
 * <b>Русский</b>: Описывает тело HTTP запроса
 * <hr>
 * <b>English</b>: Describes HTTP request body
 */
public interface HttpBodyInput extends HttpBody {
    InputStream asInputStream();
}

package io.koraframework.http.server.netty.request;

import io.netty.handler.codec.http.HttpRequest;

public interface KoraHttpRequest extends HttpRequest {
    /**
     * Возвращает распарсенное значение Content-Length (>= 0), если заголовок присутствовал,
     * или -1, если заголовок Content-Length отсутствовал в запросе.
     */
    long knownContentLength();
}

package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpHandler;

public interface HttpHandlerConfigurer {

    HttpHandler configure(HttpHandler handler);
}

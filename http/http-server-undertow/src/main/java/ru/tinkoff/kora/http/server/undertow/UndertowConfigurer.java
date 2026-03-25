package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;

public interface UndertowConfigurer {

    Undertow.Builder configure(Undertow.Builder undertow);
}

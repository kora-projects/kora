package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.application.graph.Lifecycle;

public interface HttpServer extends Lifecycle {
    int port();
}

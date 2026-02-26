package ru.tinkoff.kora.http.server.common;

public enum NoopHttpServer implements HttpServer {

    INSTANCE;

    @Override
    public int port() {
        return -1;
    }

    @Override
    public void init() {
        // no-op
    }

    @Override
    public void release() {
        // no-op
    }
}

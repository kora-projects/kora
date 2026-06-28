import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.grpc.client {
    requires transitive kora.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;
    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires transitive io.grpc.okhttp;
    requires okhttp3;

    exports io.koraframework.grpc.client;
    exports io.koraframework.grpc.client.channel;
    exports io.koraframework.grpc.client.config;
    exports io.koraframework.grpc.client.interceptor;
    exports io.koraframework.grpc.client.telemetry;
    exports io.koraframework.grpc.client.telemetry.impl;
}

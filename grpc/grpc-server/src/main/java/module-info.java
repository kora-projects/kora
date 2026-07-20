import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.grpc.server {
    requires transitive kora.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;
    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires transitive io.grpc.okhttp;
    requires io.grpc.services;
    requires com.google.protobuf;

    exports io.koraframework.grpc.server;
    exports io.koraframework.grpc.server.handler;
    exports io.koraframework.grpc.server.interceptor;
    exports io.koraframework.grpc.server.telemetry;
    exports io.koraframework.grpc.server.telemetry.impl;
}

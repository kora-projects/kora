import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.opentelemetry.tracing.exporter.grpc {
    requires transitive kora.opentelemetry.tracing;
    requires transitive kora.config.common;
    requires transitive io.opentelemetry.exporter.otlp;
    requires okhttp3;
    requires okio;

    exports io.koraframework.opentelemetry.tracing.exporter.grpc;
}

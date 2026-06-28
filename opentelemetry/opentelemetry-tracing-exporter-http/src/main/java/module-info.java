import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.opentelemetry.tracing.exporter.http {
    requires transitive kora.opentelemetry.tracing;
    requires transitive kora.config.common;
    requires transitive io.opentelemetry.exporter.sender.jdk.internal;
    requires transitive io.opentelemetry.exporter.otlp;

    exports io.koraframework.opentelemetry.tracing.exporter.http;
}

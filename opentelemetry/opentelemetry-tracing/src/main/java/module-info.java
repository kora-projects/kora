import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.opentelemetry.tracing {
    requires transitive kora.config.common;
    requires transitive kora.opentelemetry.common;
    requires transitive io.opentelemetry.sdk.trace;

    exports io.koraframework.opentelemetry.tracing;
}

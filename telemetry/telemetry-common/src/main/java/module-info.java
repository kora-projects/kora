module kora.telemetry.common {
    requires transitive kora.config.common;
    requires transitive io.opentelemetry.semconv;
    requires transitive io.opentelemetry.semconv.incubating;

    exports io.koraframework.telemetry.common;
}

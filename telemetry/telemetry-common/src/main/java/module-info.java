module kora.telemetry.common {
    requires transitive kora.config.common;
    requires transitive io.opentelemetry.semconv;
    requires transitive io.opentelemetry.semconv.incubating;

    exports ru.tinkoff.kora.telemetry.common;
}

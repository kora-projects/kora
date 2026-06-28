import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.opentelemetry.common {
    requires transitive kora.common;
    requires transitive kora.logging.common;
    requires transitive io.opentelemetry.context;
    requires transitive io.opentelemetry.semconv;
    requires transitive io.opentelemetry.semconv.incubating;
    requires transitive io.opentelemetry.api;
}

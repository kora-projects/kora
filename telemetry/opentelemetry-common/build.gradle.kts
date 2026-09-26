plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.core.common)
    api(projects.logging.loggingCommon)
    api(libs.opentelemetry.context)
    api(libs.opentelemetry.semconv)
    api(libs.opentelemetry.semconv.incubating)
    api(libs.opentelemetry.api)
}

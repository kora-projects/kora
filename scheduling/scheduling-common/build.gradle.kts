plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.core.common)
    api(projects.telemetry.telemetryCommon)
    api(projects.logging.loggingCommon)
}

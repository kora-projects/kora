plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.core.common)
    api(projects.logging.loggingCommon)
    api(projects.config.configCommon)
    api(projects.telemetry.telemetryCommon)
    api(libs.jms.api)
}

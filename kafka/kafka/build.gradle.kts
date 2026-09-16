plugins {
    alias(libs.plugins.kora.kotlin)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.core.common)
    api(projects.config.configCommon)
    api(projects.telemetry.telemetryCommon)
    api(projects.logging.loggingCommon)
    api(libs.kafka.client)

    testImplementation(projects.internal.testKafka)
}

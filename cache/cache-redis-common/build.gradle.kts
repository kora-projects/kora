plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.cache.cacheCommon)
    api(projects.config.configCommon)
    api(projects.telemetry.telemetryCommon)
    api(projects.logging.loggingCommon)

    testImplementation(projects.internal.testLogging)
    testImplementation(projects.internal.testRedis)
}

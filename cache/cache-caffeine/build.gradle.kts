plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.cache.cacheCommon)
    api(projects.config.configCommon)
    api(projects.telemetry.telemetryCommon)
    api(libs.caffeine)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.core.annotationProcessorCommon)
    testImplementation(projects.aop.aopAnnotationProcessor)
    testImplementation(projects.cache.cacheAnnotationProcessor)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(projects.core.koraAppAnnotationProcessor)
    testImplementation(projects.internal.testLogging)
}

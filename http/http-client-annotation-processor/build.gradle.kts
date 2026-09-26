plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)
    implementation(projects.core.koraAppAnnotationProcessor)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.http.httpClientCommon)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(projects.core.common)
    testImplementation(projects.logging.loggingCommon)
}

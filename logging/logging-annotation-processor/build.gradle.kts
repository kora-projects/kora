plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.aop.aopAnnotationProcessor)

    testImplementation(projects.logging.loggingCommon)
    testImplementation(projects.json.jsonAnnotationProcessor)
    testImplementation(projects.core.koraAppAnnotationProcessor)
    testImplementation(projects.logging.loggingLogback)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

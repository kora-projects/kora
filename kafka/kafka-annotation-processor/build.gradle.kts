plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)
    implementation(projects.core.koraAppAnnotationProcessor)

    testImplementation(projects.kafka.kafka)
    testImplementation(projects.config.configCommon)
    testImplementation(projects.logging.loggingCommon)
    testImplementation(projects.aop.aopAnnotationProcessor)
    testImplementation(projects.logging.loggingAnnotationProcessor)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

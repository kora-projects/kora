plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)
    implementation(projects.core.koraAppAnnotationProcessor)
    implementation(projects.aop.aopAnnotationProcessor)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.validation.validationCommon)
    testImplementation(projects.core.annotationProcessorCommon)
    testImplementation(projects.core.koraAppAnnotationProcessor)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.json.jsonCommon)
}

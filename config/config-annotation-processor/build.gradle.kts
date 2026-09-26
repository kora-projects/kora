plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.annotationProcessorCommon)

    implementation(projects.core.koraAppAnnotationProcessor)

    testImplementation(projects.config.configCommon)
    testImplementation(projects.validation.validationAnnotationProcessor)
    testImplementation(projects.validation.validationCommon)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

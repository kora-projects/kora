plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.koraAppAnnotationProcessor)

    testImplementation(libs.mapstruct)
    testImplementation(libs.mapstruct.processor)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(libs.jakarta.inject.api)
}

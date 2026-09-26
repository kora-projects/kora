plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    implementation(projects.config.configCommon)
    implementation(projects.http.httpServerCommon)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

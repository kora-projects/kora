plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.http.httpClientCommon)
    api(libs.okhttp)
    api(libs.okio)

    testImplementation(testFixtures(projects.http.httpClientCommon))
    testImplementation(libs.jackson.coreutils)
}

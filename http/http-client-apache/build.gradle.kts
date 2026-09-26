plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.http.httpClientCommon)
    api(libs.apache.httpclient)

    testImplementation(testFixtures(projects.http.httpClientCommon))
    testImplementation(libs.jackson.coreutils)
}

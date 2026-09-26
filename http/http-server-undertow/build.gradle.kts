plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.core.common)
    api(projects.http.httpServerCommon)
    api(projects.logging.loggingCommon)
    api(libs.undertow.core)
    api(libs.jboss.threads)
    api(libs.jboss.logging)

    testImplementation(testFixtures(projects.http.httpServerCommon))
}

plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.telemetry.telemetryCommon)
    api(projects.http.httpClientCommon)
    api(projects.config.configCommon)
    api(projects.core.common)

    implementation(projects.logging.loggingCommon)

    testImplementation(projects.internal.testLogging)
    testImplementation(projects.http.httpClientOk)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.s3client.minio)
    testImplementation(libs.s3client.minio.admin)
    testImplementation(libs.okhttp.logging.interceptor)
}

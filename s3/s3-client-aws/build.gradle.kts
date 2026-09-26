plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    implementation(projects.logging.loggingCommon)

    api(projects.telemetry.telemetryCommon)
    api(projects.http.httpClientCommon)
    api(projects.config.configCommon)
    api(projects.core.common)
    api(libs.s3client.aws) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }

    testImplementation(projects.internal.testLogging)
    testImplementation(libs.testcontainers.core)
}

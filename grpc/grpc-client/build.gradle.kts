plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.protobuf)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.logging.loggingCommon)
    api(projects.telemetry.telemetryCommon)
    api(libs.grpc.stub)
    api(libs.grpc.okhttp) {
        exclude(group = "com.squareup.okhttp3")
    }
    implementation(libs.okhttp.jvm)

    testImplementation(libs.grpc.java.gen)
    testImplementation(libs.grpc.protobuf)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.javax.annotation.api)
}

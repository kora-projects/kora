plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.core.common)
    api(projects.json.jsonCommon)
    api(projects.grpc.grpcClient)
    api(libs.zeebe.model)
    api(libs.zeebe.client) {
        exclude(group = "io.netty")
    }
    api(libs.netty.handlers)
    api(libs.netty.common)

    implementation(projects.config.configCommon)

    testImplementation(projects.internal.testLogging)
    testImplementation(projects.test.testJunit5)
}

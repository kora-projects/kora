plugins {
    alias(libs.plugins.kora.java)
    `java-test-fixtures`
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.logging.loggingCommon)
    api(projects.http.httpCommon)
    api(projects.telemetry.telemetryCommon)

    testImplementation(projects.config.configHocon)

    testFixturesImplementation(libs.slf4j.api)
    testFixturesImplementation(libs.logback.classic)
    testFixturesImplementation(projects.telemetry.opentelemetryTracing)
    testFixturesImplementation(libs.mockserver.netty) {
//        exclude group: 'io.swagger.parser.v3', module: 'swagger-parser'
    }
    testFixturesImplementation(libs.mockserver.client) {
//        exclude group: 'io.swagger.parser.v3', module: 'swagger-parser'
    }
    testFixturesImplementation(libs.swagger.models) {
//        exclude group: 'com.fasterxml.jackson.core', module: 'jackson-core'
//        exclude group: 'com.fasterxml.jackson.core', module: 'jackson-databind'
    }
    testFixturesImplementation(libs.bundles.netty)
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.assertj)
    testFixturesImplementation(libs.mockito.core)
    testFixturesImplementation(libs.kotlin.stdlib.lib)
}

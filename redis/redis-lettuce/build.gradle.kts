plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(libs.netty.transports)
    api(libs.apache.pool)
    api(libs.bundles.netty)
    api(libs.netty.resolver.dns)
    api(libs.lettuce.core) {
        exclude(group = "io.netty")
    }
    api(projects.core.common)
    api(projects.nettyCommon)
    api(projects.telemetry.telemetryCommon)
    api(projects.logging.loggingCommon)

    testImplementation(projects.internal.testLogging)
    testImplementation(projects.internal.testRedis)
}

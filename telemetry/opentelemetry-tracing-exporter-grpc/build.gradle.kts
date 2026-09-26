plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.telemetry.opentelemetryTracing)
    api(libs.opentelemetry.exporter.otlp) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
        exclude(group = "com.squareup.okio", module = "okio")
    }

    implementation(libs.okhttp.jvm)
    implementation(libs.okio)
}

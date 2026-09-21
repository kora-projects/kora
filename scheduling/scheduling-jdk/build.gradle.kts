plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.scheduling.schedulingCommon)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}

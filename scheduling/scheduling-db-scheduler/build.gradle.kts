plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.scheduling.schedulingCommon)
    api(libs.db.scheduler)

    implementation(projects.logging.loggingCommon)
}

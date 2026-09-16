plugins {
    alias(libs.plugins.kora.kotlin)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.database.databaseCommon)
    api(projects.core.common)
    api(libs.hikari)

    testImplementation(projects.internal.testPostgres)
}

plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.database.databaseJdbc)
    api(libs.liquibase)

    testImplementation(projects.internal.testPostgres)
}

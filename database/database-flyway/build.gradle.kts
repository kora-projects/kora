plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    api(projects.database.databaseJdbc)
    api(libs.flyway)

    testImplementation(projects.internal.testPostgres)
    testImplementation(libs.flyway.postgres)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}

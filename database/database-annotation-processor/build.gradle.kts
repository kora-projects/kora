plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.annotationProcessorCommon)

    implementation(projects.core.koraAppAnnotationProcessor)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.database.databaseCommon)
    testImplementation(projects.database.databaseJdbc)
    testImplementation(projects.database.databaseCassandra)
}

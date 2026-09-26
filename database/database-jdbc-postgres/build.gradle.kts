plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.database.databaseJdbc)
    api(projects.core.common)
    api(projects.json.jsonCommon)
    api(libs.jdbc.postgresql)

    testImplementation(projects.internal.testPostgres)
    testImplementation(libs.mockito.core)
}

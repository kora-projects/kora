plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    alias(libs.plugins.kora.hints)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.annotationProcessorCommon)

    implementation(libs.jackson.core)

    testImplementation(projects.json.jsonCommon)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))

    koraHints(projects.json.jsonCommon)
    koraHints(projects.json.jsonAnnotationProcessor)
}

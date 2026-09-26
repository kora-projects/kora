plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(libs.mockito.kotlin)
}

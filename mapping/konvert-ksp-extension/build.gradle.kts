plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.koraAppSymbolProcessor)

    testImplementation(libs.konvert.api)
    testImplementation(projects.core.koraAppSymbolProcessor)
    testImplementation(projects.core.symbolProcessorCommon)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
}

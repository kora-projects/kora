plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    alias(libs.plugins.jmh)
}

dependencies {
    api(projects.core.symbolProcessorCommon)
    api(projects.core.koraAppSymbolProcessor)

    testImplementation(projects.json.jsonCommon)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
}

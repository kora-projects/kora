plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.symbolProcessorCommon)
    implementation(projects.core.koraAppSymbolProcessor)
    implementation(libs.ksp.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(projects.http.httpClientCommon)
    testImplementation(projects.config.configSymbolProcessor)
    testImplementation(projects.logging.loggingCommon)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.stdlib.lib)
}

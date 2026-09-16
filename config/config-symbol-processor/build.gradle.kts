plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.symbolProcessorCommon)
    implementation(projects.core.koraAppSymbolProcessor)
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(projects.config.configCommon)
    testImplementation(projects.validation.validationCommon)
    testImplementation(projects.validation.validationSymbolProcessor)
    testImplementation(libs.mockito.kotlin)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

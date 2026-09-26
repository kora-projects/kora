plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.core.symbolProcessorCommon)
    implementation(projects.core.koraAppSymbolProcessor)
    implementation(projects.aop.aopSymbolProcessor)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(projects.validation.validationCommon)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.core.symbolProcessorCommon)
    testImplementation(projects.config.configSymbolProcessor)
    testImplementation(projects.core.koraAppSymbolProcessor)
    testImplementation(projects.json.jsonCommon)
    testImplementation(libs.kotlin.stdlib.lib)
}

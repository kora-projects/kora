plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.core.koraAppSymbolProcessor)
    implementation(libs.ksp.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(projects.internal.testLogging)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(libs.kotlin.stdlib.lib)
    testImplementation(projects.experimental.camundaZeebeWorker)
    testImplementation(libs.mockito.core)
}

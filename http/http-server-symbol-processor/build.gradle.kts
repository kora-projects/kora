plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.common)
    api(projects.http.httpServerCommon)
    api(projects.core.symbolProcessorCommon)
    api(projects.core.koraAppSymbolProcessor)

    implementation(libs.ksp.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(projects.json.jsonCommon)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.stdlib.lib)
}

plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    implementation(projects.core.koraAppSymbolProcessor)
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.stdlib.lib)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.config.configCommon)
    testImplementation(projects.logging.loggingLogback)
    testImplementation(projects.aop.aopSymbolProcessor)
    testImplementation(projects.config.configSymbolProcessor)
    testImplementation(projects.logging.loggingSymbolProcessor)
    testImplementation(projects.database.databaseCommon)
    testImplementation(projects.database.databaseJdbc)
    testImplementation(projects.database.databaseCassandra)
}

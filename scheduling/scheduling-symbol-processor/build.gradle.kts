plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(projects.core.koraAppSymbolProcessor)
    testImplementation(projects.config.configSymbolProcessor)
    testImplementation(projects.scheduling.schedulingJdk)
    testImplementation(projects.scheduling.schedulingQuartz)
    testImplementation(projects.scheduling.schedulingDbScheduler)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
}

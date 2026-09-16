plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    testImplementation(projects.kafka.kafka)
    testImplementation(projects.config.configCommon)
    testImplementation(projects.logging.loggingCommon)
    testImplementation(projects.aop.aopSymbolProcessor)
    testImplementation(projects.logging.loggingSymbolProcessor)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
}

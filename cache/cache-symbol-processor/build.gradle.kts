plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.aop.aopSymbolProcessor)

    testImplementation(libs.prometheus.collector.caffeine)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.cache.cacheCaffeine)
    testImplementation(projects.cache.cacheRedisLettuce)
    testImplementation(projects.json.jsonCommon)
    testImplementation(projects.config.configCommon)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(libs.kotlin.stdlib.lib)
}

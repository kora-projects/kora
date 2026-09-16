plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.aop.aopAnnotationProcessor)
    implementation(libs.javapoet)

    testImplementation(libs.prometheus.collector.caffeine)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.cache.cacheCaffeine)
    testImplementation(projects.cache.cacheRedisLettuce)
    testImplementation(projects.json.jsonCommon)
    testImplementation(projects.config.configCommon)
}

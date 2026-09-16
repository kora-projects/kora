plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)
    implementation(projects.aop.aopAnnotationProcessor)
    implementation(libs.javapoet)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.core.annotationProcessorCommon)
    testImplementation(projects.aop.aopAnnotationProcessor)
    testImplementation(projects.core.koraAppAnnotationProcessor)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(projects.config.configHocon)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.resilient.resilientKora)
    testImplementation(projects.resilient.resilientKoraDistributed)
}

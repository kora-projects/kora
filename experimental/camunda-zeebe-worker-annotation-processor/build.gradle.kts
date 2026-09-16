plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)
    implementation(projects.core.koraAppAnnotationProcessor)
    implementation(libs.javapoet)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(projects.aop.aopAnnotationProcessor)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.experimental.camundaZeebeWorker)
}

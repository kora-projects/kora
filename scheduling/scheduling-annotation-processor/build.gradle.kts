plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    implementation(projects.core.annotationProcessorCommon)

    testImplementation(projects.config.configCommon)
    testImplementation(projects.scheduling.schedulingQuartz)
    testImplementation(projects.scheduling.schedulingJdk)
    testImplementation(projects.scheduling.schedulingDbScheduler)
    testImplementation(projects.config.configAnnotationProcessor)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

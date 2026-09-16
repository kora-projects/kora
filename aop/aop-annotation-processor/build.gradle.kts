plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.annotationProcessorCommon)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

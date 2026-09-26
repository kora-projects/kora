plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.annotationProcessorCommon)
    api(projects.core.koraAppAnnotationProcessor)

    testImplementation(projects.json.jsonCommon)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
}

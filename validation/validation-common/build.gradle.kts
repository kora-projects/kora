plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(projects.core.common)

    implementation(projects.config.configCommon)

    testImplementation(projects.internal.testLogging)
}

plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.core.common)

    testImplementation(projects.internal.testLogging)
}

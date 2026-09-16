plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.core.common)
    api(projects.logging.loggingLogback)
}

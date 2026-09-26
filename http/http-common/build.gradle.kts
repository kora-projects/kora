plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    compileOnly(projects.json.jsonCommon)

    api(projects.core.common)
    api(projects.logging.loggingCommon)
}

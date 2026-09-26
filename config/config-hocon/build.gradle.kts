plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.config.configCommon)
    api(libs.typesafe.config)
}

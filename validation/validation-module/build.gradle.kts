plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    compileOnly(projects.http.httpServerCommon)

    api(projects.validation.validationCommon)
}

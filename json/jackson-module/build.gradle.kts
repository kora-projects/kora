plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    compileOnly(projects.http.httpServerCommon)
    compileOnly(projects.http.httpClientCommon)

    api(libs.jackson.databind)
}

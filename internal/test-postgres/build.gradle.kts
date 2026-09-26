plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(libs.testcontainers.postgresql)
    api(libs.jdbc.postgresql)

    implementation(libs.jspecify)
}

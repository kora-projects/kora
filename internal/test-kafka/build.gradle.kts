plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(libs.testcontainers.kafka)
    api(libs.kafka.client)

    implementation(libs.jspecify)
}

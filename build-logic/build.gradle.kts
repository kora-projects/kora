plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
}

gradlePlugin {
    plugins {
        register("kora-root-publish") {
            id = "io.koraframework.kora-root-publish"
            implementationClass = "io.koraframework.gradle.publish.KoraRootPublishConventionPlugin"
        }
    }
}

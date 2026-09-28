plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
}

gradlePlugin {
    plugins {
        register("kora-dependency-management") {
            id = "io.koraframework.kora-dependency-management"
            implementationClass = "io.koraframework.gradle.dependencies.KoraDependencyManagementConventionPlugin"
        }
    }
}

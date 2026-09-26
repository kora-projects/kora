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
        register("kora-java-module") {
            id = "io.koraframework.kora-java-module"
            implementationClass = "io.koraframework.gradle.KoraJavaModuleConventionPlugin"
        }
        register("kora-kotlin-module") {
            id = "io.koraframework.kora-kotlin-module"
            implementationClass = "io.koraframework.gradle.KoraKotlinModuleConventionPlugin"
        }
        register("kora-java") {
            id = "io.koraframework.kora-java"
            implementationClass = "io.koraframework.gradle.KoraJavaConventionPlugin"
        }
        register("kora-kotlin") {
            id = "io.koraframework.kora-kotlin"
            implementationClass = "io.koraframework.gradle.KoraKotlinConventionPlugin"
        }
        register("kora-dependency-alignment") {
            id = "io.koraframework.kora-dependency-alignment"
            implementationClass = "io.koraframework.gradle.KoraDependencyAlignmentConventionPlugin"
        }
        register("kora-experimental") {
            id = "io.koraframework.kora-experimental"
            implementationClass = "io.koraframework.gradle.KoraExperimentalConventionPlugin"
        }
        register("kora-soap") {
            id = "io.koraframework.kora-soap"
            implementationClass = "io.koraframework.gradle.soap.KoraSoapConventionPlugin"
        }
        register("kora-hints") {
            id = "io.koraframework.kora-hints"
            implementationClass = "io.koraframework.gradle.hint.KoraHintsConventionPlugin"
        }
        register("kora-root-ci-test") {
            id = "io.koraframework.kora-root-ci-test"
            implementationClass = "io.koraframework.gradle.test.KoraRootCiTestConventionPlugin"
        }
        register("kora-module-ci-test") {
            id = "io.koraframework.kora-module-ci-test"
            implementationClass = "io.koraframework.gradle.test.KoraModuleCiTestConventionPlugin"
        }
        register("kora-test") {
            id = "io.koraframework.kora-test"
            implementationClass = "io.koraframework.gradle.test.KoraTestConventionPlugin"
        }
        register("kora-root-coverage") {
            id = "io.koraframework.kora-root-coverage"
            implementationClass = "io.koraframework.gradle.test.KoraRootCoverageConventionPlugin"
        }
        register("kora-module-coverage") {
            id = "io.koraframework.kora-module-coverage"
            implementationClass = "io.koraframework.gradle.test.KoraModuleCoverageConventionPlugin"
        }
        register("kora-in-test-generated") {
            id = "io.koraframework.kora-in-test-generated"
            implementationClass = "io.koraframework.gradle.test.KoraInTestGeneratedConventionPlugin"
        }
        register("kora-root-publish") {
            id = "io.koraframework.kora-root-publish"
            implementationClass = "io.koraframework.gradle.publish.KoraRootPublishConventionPlugin"
        }
        register("kora-module-publish") {
            id = "io.koraframework.kora-module-publish"
            implementationClass = "io.koraframework.gradle.publish.KoraModulePublishConventionPlugin"
        }
        register("kora-dependency-management") {
            id = "io.koraframework.kora-dependency-management"
            implementationClass = "io.koraframework.gradle.dependencies.KoraDependencyManagementConventionPlugin"
        }
    }
}

package io.koraframework.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class KoraJavaModuleConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val pluginManager = project.pluginManager

        pluginManager.apply("java")

        pluginManager.apply("io.koraframework.kora-module-testcontainers")
        pluginManager.apply("io.koraframework.kora-java")
        pluginManager.apply("io.koraframework.kora-dependency-alignment")
        pluginManager.apply("io.koraframework.kora-experimental")
        pluginManager.apply("io.koraframework.kora-test")
        pluginManager.apply("io.koraframework.kora-module-ci-test")
        pluginManager.apply("io.koraframework.kora-module-publish")
        pluginManager.apply("io.koraframework.kora-module-coverage")
    }
}

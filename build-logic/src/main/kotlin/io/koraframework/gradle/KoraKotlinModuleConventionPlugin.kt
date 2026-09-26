package io.koraframework.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class KoraKotlinModuleConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val pluginManager = project.pluginManager

        pluginManager.apply("io.koraframework.kora-java-module")
        pluginManager.apply("io.koraframework.kora-kotlin")
    }
}

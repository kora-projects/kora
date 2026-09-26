package io.koraframework.gradle.soap

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class KoraSoapConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val cxfConfiguration = project.configurations.create("cxf")
        project.extensions.create<KoraSoapExtension>("koraSoap", project, cxfConfiguration)
    }
}

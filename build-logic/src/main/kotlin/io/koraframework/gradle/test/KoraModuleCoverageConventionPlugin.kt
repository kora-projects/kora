package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class KoraModuleCoverageConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.childProjects.isNotEmpty() || project.name == "kora-bom") return

        project.pluginManager.apply("jacoco")

        project.tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        project.tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                excludes = listOf("**.packageFor**")
            }
        }
    }
}

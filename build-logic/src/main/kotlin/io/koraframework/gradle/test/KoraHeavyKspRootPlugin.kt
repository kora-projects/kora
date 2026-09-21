package io.koraframework.gradle.test

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class KoraHeavyKspRootPlugin : Plugin<Project> {
    override fun apply(rootProject: Project) {
        if (rootProject != rootProject.rootProject) return

        rootProject.tasks.register("runHeavyKspModules", DefaultTask::class.java) {
            group = "build"
            description = "Runs tests for heavy Kora modules natively in the main build graph."

            rootProject.subprojects.forEach { sub ->
                if (sub.name != "kora-bom" && !sub.name.endsWith("-bom") && sub.name != "internal" && !sub.path.contains(":internal:")) {
                    val isHeavy = sub.name.contains("symbol-processor", ignoreCase = true) ||
                        sub.name.contains("ksp", ignoreCase = true)

                    if (isHeavy) {
                        this.dependsOn("${sub.path}:test")
                    }
                }
            }
        }
    }
}

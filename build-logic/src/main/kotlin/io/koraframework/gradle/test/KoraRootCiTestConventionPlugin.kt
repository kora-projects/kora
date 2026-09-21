package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project

class KoraRootCiTestConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project != project.gradle.rootProject) {
            throw IllegalStateException("The CI Test plugin must be applied to the root project only.")
        }

        CiTestType.TYPES.forEach { type ->
            createTasks(project, type)
        }
    }

    private fun createTasks(rootProject: Project, type: String) {
        rootProject.tasks.register("classes-$type") {
            group = "build"
        }
        rootProject.tasks.register("testClasses-$type") {
            group = "build"
        }
        rootProject.tasks.register("test-$type") {
            group = "verification"
        }
        rootProject.tasks.register("javadoc-$type") {
            group = "documentation"
        }
    }
}

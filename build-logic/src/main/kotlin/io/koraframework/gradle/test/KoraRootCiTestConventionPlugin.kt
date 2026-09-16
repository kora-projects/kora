package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project

class KoraRootCiTestConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project != project.gradle.rootProject) {
            throw IllegalStateException("The CI Test plugin must be applied to the root project only.")
        }

        val types = listOf(
            "postgres",
            "cassandra",
            "redis",
            "kafka",
            "openapi",
            "codegen-java",
            "codegen-kotlin-1",
            "codegen-kotlin-2",
            "other",
        )
        types.forEach { type ->
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

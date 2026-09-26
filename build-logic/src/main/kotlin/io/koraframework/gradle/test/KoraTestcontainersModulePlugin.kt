package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

class KoraTestcontainersModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project == project.rootProject) return
        if (project.name == "kora-bom" || project.name.endsWith("-bom")) return
        if (project.name == "internal" || project.path.contains(":internal:")) return

        val checkTcTask = project.tasks.register("checkTestcontainers", CheckTestcontainersTask::class.java) {
            this.runtimeClasspath.from(project.configurations.named("testRuntimeClasspath"))
            this.outputFile.set(project.layout.buildDirectory.file("kora-tc-marker/has-testcontainers.txt"))
        }

        val testcontainersElements = project.configurations.create("koraTestcontainersElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute(Attribute.of("io.koraframework.testcontainers", String::class.java), "true")
        }

        testcontainersElements.outgoing.artifact(checkTcTask.flatMap { it.outputFile }) {
            type = "file"
        }
    }
}

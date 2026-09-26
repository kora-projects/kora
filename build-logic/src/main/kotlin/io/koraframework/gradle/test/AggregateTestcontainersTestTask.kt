package io.koraframework.gradle.test

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.GradleConnector
import java.io.File

abstract class AggregateTestcontainersTestTask : DefaultTask() {
    @get:Internal
    abstract val tcArtifacts: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputFiles: FileCollection
        get() = tcArtifacts.get().artifactFiles

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun run() {
        val tasksToRun = mutableListOf<String>()
        val collection = tcArtifacts.get()

        collection.artifacts.forEach { resolvedArtifact ->
            val id = resolvedArtifact.id.componentIdentifier
            val markerFile = resolvedArtifact.file

            if (markerFile.exists() && markerFile.readText().trim() == "true") {
                if (id is ProjectComponentIdentifier) {
                    tasksToRun.add("${id.projectPath}:test")
                }
            }
        }

        if (tasksToRun.isEmpty()) {
            logger.lifecycle("No modules with Testcontainers found.")
            return
        }

        logger.lifecycle("Launching tests for modules: $tasksToRun")

        val connector = GradleConnector.newConnector()
            .forProjectDirectory(rootDir.get().asFile)

        connector.connect().use { connection ->
            val build = connection.newBuild()
                .forTasks(*tasksToRun.toTypedArray())
                .addArguments("--build-cache", "--parallel", "--profile")
                .setStandardOutput(System.out)
                .setStandardError(System.err)

            try {
                build.run()
            } catch (e: Exception) {
                throw org.gradle.api.tasks.TaskExecutionException(this, e)
            }
        }
    }
}

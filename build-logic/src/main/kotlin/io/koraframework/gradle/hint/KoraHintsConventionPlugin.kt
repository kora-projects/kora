package io.koraframework.gradle.hint

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class KoraHintsConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val localHintsFile = project.layout.projectDirectory.file("src/main/resources/kora-module-hints.json")
        val localTargetDirProvider = project.layout.buildDirectory.dir("kora-hints-generated")
        val currentProjectPath = project.path

        val koraHintsConfig = project.configurations.create("koraHints") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "kora-hints"))
            }
        }

        val koraHintsElements = project.configurations.create("koraHintsElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "kora-hints"))
            }
        }

        koraHintsElements.outgoing.artifact(localHintsFile) {
        }

        val safeFilesCollection = project.objects.fileCollection().from(koraHintsConfig)

        val buildHints = project.tasks.register<MergeHintsTask>("buildHints") {
            projectPath.set(currentProjectPath)

            hintFiles.from(safeFilesCollection)
            hintFiles.from(localHintsFile)

            resultFile.set(localTargetDirProvider.map { it.file("kora-hints.json") })
        }

        project.pluginManager.withPlugin("java") {
            val javaExtension = project.extensions.getByType<JavaPluginExtension>()

            val generatedResources = project.files(localTargetDirProvider).builtBy(buildHints)
            javaExtension.sourceSets.getByName("main").resources {
                srcDir(generatedResources)
            }

            javaExtension.sourceSets.getByName("test").resources {
                srcDir(generatedResources)
            }

            project.tasks.named<Copy>("processResources") {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            project.tasks.named<Copy>("processTestResources") {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }
}

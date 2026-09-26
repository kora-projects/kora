package io.koraframework.gradle.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register

class KoraRootPublishConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project != project.rootProject) return

        val rootVersion = project.version.toString()
        val rootBuildDir = project.layout.buildDirectory

        val publishingAggregation = project.configurations.create("publishingAggregation") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        val bomSourceElements = project.configurations.create("bomSourceElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
        }

        val cleanPublishDir = project.tasks.register<Delete>("cleanPublishDir") {
            delete(project.layout.buildDirectory.dir("publishing-repository"))
            group = "publishing"
        }

        project.tasks.register<Zip>("createPublishArchive") {
            group = "publishing"
            archiveFileName.set("deployment.zip")
            destinationDirectory.set(rootBuildDir)
            from(rootBuildDir.dir("publishing-repository"))
            include("*/**")
            exclude("*/**/maven-metadata.*")

            dependsOn(cleanPublishDir)
            dependsOn(":kora-bom:publishMavenPublicationToBuildRepository")
            dependsOn(publishingAggregation.incoming.artifactView { }.files)
        }

        val publishedSubprojects = project.subprojects.filter {
            it.childProjects.isEmpty()
                && it.name != "kora-bom"
                && !it.path.contains(":internal:")
                && it.parent?.name != "internal"
        }

        publishingAggregation.dependencies.addAllLater(project.provider {
            publishedSubprojects.map {
                project.dependencies.project(mapOf("path" to it.path, "configuration" to "publishingElements"))
            }
        })

        bomSourceElements.dependencies.addAllLater(project.provider {
            publishedSubprojects.map {
                project.dependencies.project(mapOf("path" to it.path))
            }
        })

        project.tasks.register<SonatypePublishTask>("uploadPublishArchive") {
            dependsOn("createPublishArchive")
            version.convention(project.providers.provider { rootVersion })
            archive.set(rootBuildDir.file("deployment.zip"))
            username.convention(
                project.providers.gradleProperty("sonatypeUser")
                    .orElse(project.providers.environmentVariable("SONATYPE_KORA_IO_USERNAME"))
                    .orElse("")
            )
            password.convention(
                project.providers.gradleProperty("sonatypePassword")
                    .orElse(project.providers.environmentVariable("SONATYPE_KORA_IO_PASSWORD"))
                    .orElse("")
            )
        }
    }
}

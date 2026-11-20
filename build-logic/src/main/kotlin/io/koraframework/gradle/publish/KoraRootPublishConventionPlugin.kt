package io.koraframework.gradle.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class KoraRootPublishConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project != project.rootProject) return

        val rootBuildDir = project.layout.buildDirectory

        project.tasks.register<SonatypePublishTask>("uploadPublishArchive") {
            dependsOn("createPublishArchive")
            version.set(project.provider { project.version }.map { it.toString() })
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

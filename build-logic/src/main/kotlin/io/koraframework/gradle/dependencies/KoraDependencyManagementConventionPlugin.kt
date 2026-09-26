package io.koraframework.gradle.dependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class KoraDependencyManagementConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.rootProject == project) {
            val projectDir = project.layout.projectDirectory

            project.tasks.register<CheckAndUpdateCatalogVersionsTask>("updateLibsVersions") {
                catalogFile.set(projectDir.file("gradle/libs.versions.toml"))

                val policyProvider = project.providers.gradleProperty("versionPolicy")
                    .orElse("gradle/libs-version-policy.toml")
                    .map { projectDir.file(it) }

                versionPolicyFile.set(policyProvider)

                val vulnReportProvider = project.providers.gradleProperty("vulnerabilityReportFile")
                    .orElse("build/reports/dependency-vulnerabilities.md")
                    .map { projectDir.file(it) }

                vulnerabilityReportFile.set(vulnReportProvider)

                val updateReportProvider = project.providers.gradleProperty("updateReportFile")
                    .orElse("build/reports/dependency-updates.md")
                    .map { projectDir.file(it) }

                updateReportFile.set(updateReportProvider)

                writeVersions.set(
                    project.providers.gradleProperty("writeVersions")
                        .map { it.toBoolean() }
                        .orElse(false)
                )

                includePreRelease.set(
                    project.providers.gradleProperty("includePreRelease")
                        .map { it.toBoolean() }
                        .orElse(false)
                )

                updateLevel.set(
                    project.providers.gradleProperty("updateLevel")
                        .orElse("any")
                )

                reportVulnerabilities.set(
                    project.providers.gradleProperty("reportVulnerabilities")
                        .map { it.toBoolean() }
                        .orElse(false)
                )

                reportUpdates.set(
                    project.providers.gradleProperty("reportUpdates")
                        .map { it.toBoolean() }
                        .orElse(false)
                )
            }
        }
    }
}

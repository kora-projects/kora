package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class KoraTestConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.childProjects.isNotEmpty() || project.name == "kora-bom") {
            return
        }

        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val libs = catalogs.named("libs")

        project.pluginManager.withPlugin("java") {
            libs.findLibrary("jspecify").ifPresent { project.dependencies.add("api", it) }

            project.dependencies.add("testImplementation", project.dependencies.project(mapOf("path" to ":internal:test-logging")))
            libs.findLibrary("junit.jupiter").ifPresent { project.dependencies.add("testImplementation", it) }
            libs.findLibrary("mockito.core").ifPresent { project.dependencies.add("testImplementation", it) }
            libs.findLibrary("assertj").ifPresent { project.dependencies.add("testImplementation", it) }

            if (project.path.contains(":internal:")) {
                libs.findLibrary("junit.platform.launcher").ifPresent { project.dependencies.add("api", it) }
                libs.findLibrary("junit.jupiter").ifPresent { project.dependencies.add("api", it) }
            }

            project.tasks.withType<Test>().configureEach {
                forkEvery = 0
                failFast = true
                failOnNoDiscoveredTests.set(false)
                environment(System.getenv())

                jvmArgs(
                    "-XX:+TieredCompilation",
                    "-XX:TieredStopAtLevel=1",
                    "-XX:+UseParallelGC",
                    "-XX:FlightRecorderOptions=stackdepth=1024",
                    "--enable-preview",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                    "--add-opens", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
                )
                useJUnitPlatform()
                testLogging {
                    showStandardStreams = false
                    showCauses = true
                    showExceptions = true
                    showStackTraces = true
                    events(TestLogEvent.FAILED)
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }
        }
    }
}

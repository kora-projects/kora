package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KoraInTestGeneratedConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        project.pluginManager.withPlugin("java") {
            val javaPlugin = project.extensions.getByType<JavaPluginExtension>()
            val sourceSets = javaPlugin.sourceSets

            val main = sourceSets.getByName("main")
            val test = sourceSets.getByName("test")

            sourceSets.maybeCreate("testGenerated").apply {
                java.srcDir(project.layout.buildDirectory.dir("in-test-generated/sources"))

                compileClasspath = compileClasspath + main.output + test.output + main.compileClasspath + test.compileClasspath
                runtimeClasspath = runtimeClasspath + main.output + test.output + main.runtimeClasspath + test.runtimeClasspath
            }
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinExtension = project.extensions.getByType<KotlinJvmProjectExtension>()

            kotlinExtension.sourceSets.maybeCreate("testGenerated").apply {
                val buildDir = project.layout.buildDirectory
                kotlin.srcDirs(
                    buildDir.dir("in-test-generated-ksp/ksp/sources/kotlin"),
                    buildDir.dir("in-test-generated-ksp/sources")
                )
            }
        }
    }
}

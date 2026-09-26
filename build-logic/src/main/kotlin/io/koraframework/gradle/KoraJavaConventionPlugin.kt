package io.koraframework.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class KoraJavaConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        project.providers.environmentVariable("KORA_VERSION").orNull?.let {
            project.version = it
        }
        if (project.childProjects.isNotEmpty() || project.name == "kora-bom") {
            return
        }

        project.pluginManager.apply("java-library")

        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val libs = catalogs.named("libs")
        val javaVersionStr = libs.findVersion("java")
            .orElseThrow { IllegalStateException("Version 'java' not found in libs.versions.toml") }
            .requiredVersion
        val javaVersionAsInt = javaVersionStr.toInt()
        val javaVersionEnum = JavaVersion.toVersion(javaVersionStr)

        project.extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaVersionAsInt))
            }
            sourceCompatibility = javaVersionEnum
            targetCompatibility = javaVersionEnum
            withSourcesJar()
            withJavadocJar()
        }

        project.tasks.withType<JavaCompile>().configureEach {
            options.encoding = Charsets.UTF_8.name()
            options.isDebug = true
            options.compilerArgs.addAll(listOf("-parameters", "-XprintRounds"))
            if (name == "compileJava" && !project.name.contains("internal")) {
                options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial,-processing,-missing-explicit-ctor"))
            }
        }

        project.tasks.withType<Javadoc>().configureEach {
            val options = options as StandardJavadocDocletOptions
            options.encoding = Charsets.UTF_8.name()
            options.addBooleanOption("html5", true)
            options.addBooleanOption("-no-fonts", true)
            options.addStringOption("Xdoclint:none", "-quiet")
        }

        if (project.childProjects.isEmpty()) {
            val moduleInfo = project.layout.projectDirectory.file("src/main/java/module-info.java")
            project.tasks.withType<Jar>().configureEach {
                manifest {
                    val automaticModuleNameProvider = project.provider {
                        if (!moduleInfo.asFile.exists()) {
                            "kora." + project.name.replace('-', '.')
                        } else {
                            null
                        }
                    }
                    attributes(mapOf("Automatic-Module-Name" to automaticModuleNameProvider))
                }
            }
        }

        project.tasks.register<DependencyReportTask>("allDeps")
    }
}

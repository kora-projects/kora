package io.koraframework.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KoraKotlinConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")

        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val libs = catalogs.named("libs")

        val javaVersionProvider = project.provider {
            libs.findVersion("java")
                .orElseThrow { IllegalStateException("Version 'java' not found in libs.versions.toml") }
                .requiredVersion
                .toInt()
        }

        project.extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain {
                languageVersion.set(javaVersionProvider.map { JavaLanguageVersion.of(it) })
            }
        }

        project.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                javaParameters.set(true)
            }
        }
    }
}

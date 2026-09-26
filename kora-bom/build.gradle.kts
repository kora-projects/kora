import io.koraframework.gradle.publish.BomPomModificationAction

plugins {
    `java-platform`
    alias(libs.plugins.kora.module.publish)
}

val bomResolver = configurations.create("bomResolver") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    bomResolver(project(":", "bomSourceElements"))

    constraints {
        bomResolver.incoming.resolutionResult.allComponents {
            val id = this.id
            if (id is ProjectComponentIdentifier && id.projectPath != project.path) {
                api(project(id.projectPath))
            }
        }
    }
}

val javaVersionStr = libs.versions.java.get()
val compilerPluginVersion = libs.versions.maven.compiler.plugin.get()
val surefirePluginVersion = libs.versions.maven.surefire.plugin.get()
val projectVersionStr = project.version.toString()

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom {
                withXml(
                    BomPomModificationAction(
                        javaVersion = javaVersionStr,
                        compilerVersion = compilerPluginVersion,
                        surefireVersion = surefirePluginVersion,
                        projectVersion = projectVersionStr
                    )
                )
            }
        }
    }
}

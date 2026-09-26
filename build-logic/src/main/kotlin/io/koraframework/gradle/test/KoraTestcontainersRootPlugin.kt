package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

class KoraTestcontainersRootPlugin : Plugin<Project> {
    override fun apply(rootProject: Project) {
        if (rootProject != rootProject.rootProject) return

        val incomingTc = rootProject.configurations.create("incomingTestcontainersMarkers") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(Attribute.of("io.koraframework.testcontainers", String::class.java), "true")
        }

        rootProject.subprojects.forEach { sub ->
            if (sub.name != "kora-bom" && !sub.name.endsWith("-bom") && sub.name != "internal" && !sub.path.contains(":internal:")) {
                rootProject.dependencies.add(incomingTc.name, sub)
            }
        }

        val lenientArtifacts = incomingTc.incoming.artifactView {
            lenient(true)
        }.artifacts

        rootProject.tasks.register("testContainersAll", AggregateTestcontainersTestTask::class.java) {
            group = "verification"
            description = "Runs tests for all Kora modules that utilize Testcontainers."
            this.tcArtifacts.set(lenientArtifacts)
            this.rootDir.set(rootProject.rootDir)
        }
    }
}

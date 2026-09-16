package io.koraframework.gradle.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.HashSet
import kotlin.math.abs

class KoraModuleCiTestConventionPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val rootProject = project.rootProject
        if (project == rootProject) return

        val fullName = getProjectFullName(rootProject, project)
        val nonOtherModules = hashSetOf(
            "internal",
            "internal:test-cassandra",
            "internal:test-kafka",
            "internal:test-logging",
            "internal:test-postgres",
            "internal:test-redis",
        )

        val targetType = getTargetCiType(project, fullName, nonOtherModules)

        createLocalMirrorTask(project, "classes", "build", targetType)
        createLocalMirrorTask(project, "testClasses", "build", targetType)
        createLocalMirrorTask(project, "test", "verification", targetType)
        createLocalMirrorTask(project, "javadoc", "documentation", targetType)
    }

    private fun createLocalMirrorTask(project: Project, taskPrefix: String, taskGroup: String, targetType: String) {
        val mirrorTaskName = "$taskPrefix-$targetType"

        project.tasks.register(mirrorTaskName) {
            group = taskGroup
            dependsOn(project.tasks.named(taskPrefix))
        }
    }

    private fun getTargetCiType(project: Project, fullName: String, nonOtherModules: HashSet<String>): String {
        val explicitMapping = mapOf(
            "database:database-common" to "postgres",
            "database:database-jdbc" to "postgres",
            "database:database-flyway" to "postgres",
            "database:database-liquibase" to "postgres",
            "database:database-jdbc-postgres" to "postgres",
            "experimental:camunda-engine-bpmn" to "postgres",
            "database:database-cassandra" to "cassandra",
            "redis:redis-lettuce" to "redis",
            "cache:cache-redis-lettuce" to "redis",
            "kafka:kafka" to "kafka",
            "openapi:openapi-generator" to "openapi",
            "openapi:openapi-management" to "openapi",
            "mapping:mapstruct-java-extension" to "codegen-java",
            "mapping:mapstruct-ksp-extension" to "codegen-kotlin-1",
            "mapping:konvert-ksp-extension" to "codegen-kotlin-2",
        )

        if (explicitMapping.containsKey(fullName)) {
            return explicitMapping[fullName]!!
        }

        if (project.name.contains("annotation-processor") && project.name !in nonOtherModules) {
            return "codegen-java"
        }

        if ((project.name.contains("symbol-processor") || project.name.contains("ksp")) && project.name !in nonOtherModules) {
            val hash = abs(fullName.hashCode())
            return if (hash % 2 == 0) "codegen-kotlin-1" else "codegen-kotlin-2"
        }

        if (project.childProjects.isEmpty() && project.name != "kora-bom" && fullName !in nonOtherModules) {
            return "other"
        }

        return "other"
    }

    private fun getProjectFullName(rootProject: Project, pj: Project): String {
        var fullName = pj.name
        var parent = pj.parent
        while (parent != null && parent.name != rootProject.name) {
            fullName = "${parent.name}:$fullName"
            parent = parent.parent
        }
        return fullName
    }
}

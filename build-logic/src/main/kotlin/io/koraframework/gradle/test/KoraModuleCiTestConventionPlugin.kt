package io.koraframework.gradle.test

import io.koraframework.gradle.test.CiTestType.CODEGEN_JAVA
import io.koraframework.gradle.test.CiTestType.CODEGEN_KOTLIN_1
import io.koraframework.gradle.test.CiTestType.CODEGEN_KOTLIN_2
import io.koraframework.gradle.test.CiTestType.CODEGEN_KOTLIN_3
import io.koraframework.gradle.test.CiTestType.CODEGEN_KOTLIN_4
import io.koraframework.gradle.test.CiTestType.OTHER
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.HashSet

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

        val targetTypes = if (fullName == "openapi:openapi-generator") {
            listOf(CiTestType.OPENAPI_SHARD_1, CiTestType.OPENAPI_SHARD_2)
        } else {
            listOf(getTargetCiType(project, fullName, nonOtherModules))
        }

        targetTypes.forEach {
            createLocalMirrorTask(project, "classes", "build", it)
            createLocalMirrorTask(project, "testClasses", "build", it)
            createLocalMirrorTask(project, "test", "verification", it)
            createLocalMirrorTask(project, "javadoc", "documentation", it)
        }
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
            "database:database-common" to CODEGEN_JAVA,
            "database:database-jdbc" to CODEGEN_JAVA,
            "database:database-flyway" to CODEGEN_JAVA,
            "database:database-liquibase" to CODEGEN_JAVA,
            "database:database-jdbc-postgres" to CODEGEN_JAVA,
            "experimental:camunda-engine-bpmn" to CODEGEN_JAVA,
            "kafka:kafka" to CODEGEN_JAVA,
            "database:database-cassandra" to CODEGEN_JAVA,
            "redis:redis-lettuce" to CODEGEN_JAVA,
            "cache:cache-redis-lettuce" to CODEGEN_JAVA,
            "mapping:mapstruct-java-extension" to CODEGEN_JAVA,
        )

        if (explicitMapping.containsKey(fullName)) {
            return explicitMapping[fullName]!!
        }

        if (project.name.contains("annotation-processor") && project.name !in nonOtherModules) {
            return CODEGEN_JAVA
        }

        if ((project.name.contains("symbol-processor") || project.name.contains("ksp")) && project.name !in nonOtherModules) {
            val fixedGroup1 = hashSetOf(
                "validation:validation-symbol-processor",
                "kafka:kafka-symbol-processor",
                "resilient:resilient-symbol-processor",
                "experimental:s3-client-symbol-processor",
            )
            val fixedGroup2 = hashSetOf(
                "database:database-symbol-processor",
                "json:json-symbol-processor",
                "mapping:konvert-ksp-extension",
                "mapping:mapstruct-ksp-extension",
            )
            val fixedGroup3 = hashSetOf(
                "http:http-client-symbol-processor",
                "http:http-server-symbol-processor",
                "http:http-soap-symbol-processor",
                "http:soap-client-symbol-processor",
                "aop:aop-symbol-processor",
                "logging:logging-symbol-processor",
            )

            if (fullName in fixedGroup1) {
                return CODEGEN_KOTLIN_1
            }
            if (fullName in fixedGroup2) {
                return CODEGEN_KOTLIN_2
            }
            if (fullName in fixedGroup3) {
                return CODEGEN_KOTLIN_3
            }
            return CODEGEN_KOTLIN_4
        }

        if (project.childProjects.isEmpty() && project.name != "kora-bom" && fullName !in nonOtherModules) {
            return OTHER
        }

        return OTHER
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

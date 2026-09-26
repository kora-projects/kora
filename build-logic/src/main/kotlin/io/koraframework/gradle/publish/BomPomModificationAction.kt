package io.koraframework.gradle.publish

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import java.io.Serializable

class BomPomModificationAction(
    private val javaVersion: String,
    private val compilerVersion: String,
    private val surefireVersion: String,
    private val projectVersion: String
) : Action<XmlProvider>, Serializable {

    override fun execute(xmlProvider: XmlProvider) {
        val pomNode = xmlProvider.asNode()

        val properties = pomNode.appendNode("properties")
        properties.appendNode("java.version", javaVersion)

        val buildNode = pomNode.appendNode("build")
        val pluginManagementNode = buildNode.appendNode("pluginManagement")
        val pluginsNode = pluginManagementNode.appendNode("plugins")

        val compilerPlugin = pluginsNode.appendNode("plugin")
        compilerPlugin.appendNode("groupId", "org.apache.maven.plugins")
        compilerPlugin.appendNode("artifactId", "maven-compiler-plugin")
        compilerPlugin.appendNode("version", compilerVersion)

        val compilerConfig = compilerPlugin.appendNode("configuration")
        compilerConfig.appendNode("release", "\${java.version}")
        compilerConfig.appendNode("source", "\${java.version}")
        compilerConfig.appendNode("target", "\${java.version}")

        val compilerArgs = compilerConfig.appendNode("compilerArgs")
        compilerArgs.appendNode("arg", "-parameters")

        val processorPaths = compilerConfig.appendNode("annotationProcessorPaths")
        val pathNode = processorPaths.appendNode("path")
        pathNode.appendNode("groupId", "io.koraframework")
        pathNode.appendNode("artifactId", "annotation-processors")
        pathNode.appendNode("version", projectVersion)

        val surefirePlugin = pluginsNode.appendNode("plugin")
        surefirePlugin.appendNode("groupId", "org.apache.maven.plugins")
        surefirePlugin.appendNode("artifactId", "maven-surefire-plugin")
        surefirePlugin.appendNode("version", surefireVersion)

        val surefireConfig = surefirePlugin.appendNode("configuration")
        surefireConfig.appendNode("argLine", "--enable-preview")
    }
}

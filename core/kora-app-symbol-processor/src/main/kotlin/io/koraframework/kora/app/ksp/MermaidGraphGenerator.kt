package io.koraframework.kora.app.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.component.ComponentDependency
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.component.ResolvedComponent
import io.koraframework.kora.app.ksp.interceptor.ComponentInterceptors

/** Exports the resolved compile-time graph; arrows point from consumers to dependencies. */
internal class MermaidGraphGenerator(private val environment: SymbolProcessorEnvironment) {
    companion object {
        const val OPTION_ENABLED = "kora.app.graph.mermaid.enabled"
    }

    private val enabled = environment.options[OPTION_ENABLED].toBoolean()

    fun generate(graph: ResolvedGraph, interceptors: ComponentInterceptors) {
        if (!enabled) return

        val diagram = StringBuilder("flowchart TD\n")
        for (component in graph.components) {
            var label = component.type.toTypeName().toString()
            component.tag?.let { label += " @Tag($it)" }
            diagram.append("    ${component.fieldName}[\"${escape(label)}\"]\n")
        }

        val edges = linkedSetOf<String>()
        for (component in graph.components) {
            for (dependency in component.dependencies) {
                addDependency(edges, component, dependency, "")
            }
            component.declaration.condition?.let {
                addEdge(edges, component, graph.conditionByTag.getValue(it), "condition")
            }
            for (condition in component.getParentConditions().sortedBy { it.canonicalName }) {
                addEdge(edges, component, graph.conditionByTag.getValue(condition), "parent condition")
            }
            for (interceptor in interceptors.interceptorsFor(component.declaration)) {
                addEdge(edges, component, interceptor.component, "interceptor")
            }
        }
        edges.forEach(diagram::append)

        val packageName = graph.root.packageName.asString()
        val fileName = graph.root.simpleName.asString() + "Graph"
        write(packageName, fileName, "mmd", diagram.toString())
        write(packageName, fileName, "md", "```mermaid\n$diagram```\n")
    }

    private fun write(packageName: String, fileName: String, extension: String, content: String) {
        environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, packageName, fileName, extension)
            .bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    private fun addDependency(edges: MutableSet<String>, source: ResolvedComponent, dependency: ComponentDependency, prefix: String) {
        when (dependency) {
            is ComponentDependency.NullDependency, is ComponentDependency.TypeOfDependency, is ComponentDependency.GraphDependency -> Unit
            is ComponentDependency.AllOfDependency -> dependency.resolvedDependencies.forEach { addDependency(edges, source, it, "All ") }
            is ComponentDependency.OneOfDependency -> dependency.dependencies.forEach { addDependency(edges, source, it, "OneOf ") }
            is ComponentDependency.PromisedProxyParameterDependency -> addEdge(edges, source, dependency.realDependency, prefix + "PromiseOf")
            is ComponentDependency.ValueOfDependency -> dependency.delegate.component?.let { addEdge(edges, source, it, prefix + "ValueOf") }
            is ComponentDependency.PromiseOfDependency -> dependency.delegate.component?.let { addEdge(edges, source, it, prefix + "PromiseOf") }
            is ComponentDependency.TargetDependency -> {
                val label = when (dependency.claim.claimType) {
                    DependencyClaim.DependencyClaimType.NODE_OF -> "Node"
                    DependencyClaim.DependencyClaimType.NULLABLE_ONE -> "nullable"
                    else -> "dependency"
                }
                addEdge(edges, source, dependency.component, prefix + label)
            }
            is ComponentDependency.WrappedTargetDependency -> addEdge(edges, source, dependency.component, prefix + "Wrapped")
        }
    }

    private fun addEdge(edges: MutableSet<String>, source: ResolvedComponent, target: ResolvedComponent, label: String) {
        edges.add("    ${source.fieldName} -->|$label| ${target.fieldName}\n")
    }

    // Mermaid entities keep generic types and other label characters out of its syntax.
    private fun escape(value: String): String = buildString {
        for (char in value) {
            append(when (char) {
                '#' -> "#35;"
                '&' -> "#38;"
                '"' -> "#quot;"
                '<' -> "#lt;"
                '>' -> "#gt;"
                '\r', '\n' -> " "
                else -> char.toString()
            })
        }
    }
}

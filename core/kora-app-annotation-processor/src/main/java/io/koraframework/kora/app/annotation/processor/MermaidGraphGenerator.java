package io.koraframework.kora.app.annotation.processor;

import io.koraframework.kora.app.annotation.processor.component.ComponentDependency;
import io.koraframework.kora.app.annotation.processor.component.ResolvedComponent;
import io.koraframework.kora.app.annotation.processor.interceptor.ComponentInterceptors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exports the resolved compile-time graph; arrows point from consumers to dependencies. */
final class MermaidGraphGenerator {
    public static final String OPTION_ENABLED = "kora.app.graph.mermaid.enabled";

    private final ProcessingEnvironment environment;
    private final boolean enabled;

    MermaidGraphGenerator(ProcessingEnvironment environment) {
        this.environment = environment;
        this.enabled = Boolean.parseBoolean(environment.getOptions().get(OPTION_ENABLED));
    }

    void generate(ResolvedGraph graph, ComponentInterceptors interceptors) throws IOException {
        if (!this.enabled) {
            return;
        }
        var diagram = new StringBuilder("flowchart TD\n");
        for (var component : graph.components()) {
            var label = component.type().toString();
            if (component.tag() != null) {
                label += " @Tag(" + component.tag() + ")";
            }
            diagram.append("    ").append(component.fieldName()).append("[\"").append(escape(label)).append("\"]\n");
        }

        var edges = new LinkedHashSet<String>();
        for (var component : graph.components()) {
            for (var dependency : component.dependencies()) {
                addDependency(edges, component, dependency, "");
            }
            if (component.declaration().condition() != null) {
                addEdge(edges, component, graph.conditionByTag().get(component.declaration().condition()), "condition");
            }
            for (var condition : component.parentConditions().stream().sorted(Comparator.comparing(c -> c.canonicalName())).toList()) {
                addEdge(edges, component, graph.conditionByTag().get(condition), "parent condition");
            }
            for (var interceptor : interceptors.interceptorsFor(component)) {
                addEdge(edges, component, interceptor.component(), "interceptor");
            }
        }
        edges.forEach(diagram::append);

        var packageName = this.environment.getElementUtils().getPackageOf(graph.root()).getQualifiedName().toString();
        var fileName = graph.root().getSimpleName() + "Graph";
        write(graph, packageName, fileName + ".mmd", diagram.toString());
        write(graph, packageName, fileName + ".md", "```mermaid\n" + diagram + "```\n");
    }

    private void write(ResolvedGraph graph, String packageName, String fileName, String content) throws IOException {
        var resource = this.environment.getFiler().createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName, graph.root());
        try (var writer = resource.openWriter()) {
            writer.write(content);
        }
    }

    private static void addDependency(Set<String> edges, ResolvedComponent source, ComponentDependency dependency, String prefix) {
        switch (dependency) {
            case ComponentDependency.NullDependency _, ComponentDependency.TypeOfDependency _, ComponentDependency.GraphDependency _ -> {}
            case ComponentDependency.AllOfDependency all -> {
                for (var target : all.getResolvedDependencies()) {
                    addDependency(edges, source, target, "All ");
                }
            }
            case ComponentDependency.OneOfDependency one -> {
                for (var target : one.dependencies()) {
                    addDependency(edges, source, target, "OneOf ");
                }
            }
            case ComponentDependency.PromisedProxyParameterDependency proxy -> addEdge(edges, source, proxy.realDependency(), prefix + "PromiseOf");
            case ComponentDependency.SingleDependency single -> {
                var label = switch (single) {
                    case ComponentDependency.PromiseOfDependency _ -> "PromiseOf";
                    case ComponentDependency.ValueOfDependency _ -> "ValueOf";
                    case ComponentDependency.WrappedTargetDependency _ -> "Wrapped";
                    case ComponentDependency.TargetDependency target -> switch (target.claim().claimType()) {
                        case NODE_OF -> "Node";
                        case ONE_NULLABLE -> "nullable";
                        default -> "dependency";
                    };
                };
                addEdge(edges, source, single.component(), prefix + label);
            }
        }
    }

    private static void addEdge(Set<String> edges, ResolvedComponent source, ResolvedComponent target, String label) {
        edges.add("    " + source.fieldName() + " -->|" + label + "| " + target.fieldName() + "\n");
    }

    // Mermaid entities keep generic types and other label characters out of its syntax.
    private static String escape(String value) {
        var result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            result.append(switch (value.charAt(i)) {
                case '#' -> "#35;";
                case '&' -> "#38;";
                case '"' -> "#quot;";
                case '<' -> "#lt;";
                case '>' -> "#gt;";
                case '\r', '\n' -> " ";
                default -> String.valueOf(value.charAt(i));
            });
        }
        return result.toString();
    }
}

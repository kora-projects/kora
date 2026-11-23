package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.JavaCompilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MermaidGraphGeneratorTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "invalid"})
    void disabledUnlessEnabled(String option) throws Exception {
        compile(option, """
            @KoraApp
            public interface App {
                @Root default String root() { return "test"; }
            }
            """);
        assertThat(directory.resolve("classes/example/AppGraph.mmd")).doesNotExist();
        assertThat(directory.resolve("classes/example/AppGraph.md")).doesNotExist();
        assertThat(directory.resolve("classes/example/AppGraph.class")).exists();
    }

    @Test
    void exportsDependenciesTagsAndInterceptors() throws Exception {
        compile("true", """
            @KoraApp
            public interface App {
                class Item {}
                class Marker {}
                class RootType {}
                class WrappedValue {}
                class Wrapper implements Wrapped<WrappedValue> {
                    public WrappedValue value() { return new WrappedValue(); }
                }
                class Interceptor implements GraphInterceptor<Item> {
                    public Item afterInit(Item value) { return value; }
                    public Item beforeRelease(Item value) { return value; }
                }
                interface Missing {}
                default Item item() { return new Item(); }
                @Tag(Marker.class) default Item taggedItem() { return new Item(); }
                default java.util.List<String> strings() { return java.util.List.of(); }
                default Interceptor interceptor() { return new Interceptor(); }
                default Wrapper wrapper() { return new Wrapper(); }
                @Root default RootType root(Item item, ValueOf<Item> value, PromiseOf<Item> promise,
                    Node<Item> node, All<Item> all, All<ValueOf<Item>> allValues, All<PromiseOf<Item>> allPromises,
                    @Tag(Marker.class) Item tagged, java.util.List<String> strings,
                    @Nullable Missing missing, TypeRef<Item> type, Graph graph, WrappedValue wrapped) {
                    return new RootType();
                }
            }
            """);
        var diagram = diagram("App");
        assertThat(diagram).startsWith("flowchart TD\n")
            .contains("java.util.List#lt;java.lang.String#gt;", "@Tag(example.App.Marker)")
            .contains("-->|dependency|", "-->|ValueOf|", "-->|PromiseOf|", "-->|Node|",
                "-->|All dependency|", "-->|All ValueOf|", "-->|All PromiseOf|", "-->|interceptor|", "-->|Wrapped|")
            .doesNotContain("Missing", "TypeRef", "java.util.List<java.lang.String>");
        var root = node(diagram, "example.App.RootType");
        var item = node(diagram, "example.App.Item");
        assertThat(diagram).contains(root + " -->|dependency| " + item);
        assertThat(Files.readString(directory.resolve("classes/example/AppGraph.md")))
            .isEqualTo("```mermaid\n" + diagram + "```\n");
        assertValidEdges(diagram);
    }

    @Test
    void exportsConditionalAlternatives() throws Exception {
        compile("true", """
            @KoraApp
            public interface App {
                interface Choice {}
                class First implements Choice {}
                class Second implements Choice {}
                class CheckFirst {}
                class CheckSecond {}
                class RootType {}
                class Leaf {}
                default Leaf leaf() { return new Leaf(); }
                @Tag(CheckFirst.class) default GraphCondition firstCondition() {
                    return () -> new GraphCondition.ConditionResult.Matched("first");
                }
                @Tag(CheckSecond.class) default GraphCondition secondCondition() {
                    return () -> new GraphCondition.ConditionResult.Matched("second");
                }
                @Conditional(tag = CheckFirst.class) default First first(Leaf leaf) { return new First(); }
                @Conditional(tag = CheckSecond.class) default Second second() { return new Second(); }
                @Root default RootType root(Choice choice) { return new RootType(); }
            }
            """);
        var diagram = diagram("App");
        var root = node(diagram, "example.App.RootType");
        assertThat(diagram).contains(root + " -->|OneOf dependency| " + node(diagram, "example.App.First"))
            .contains(root + " -->|OneOf dependency| " + node(diagram, "example.App.Second"))
            .contains("-->|condition|", "-->|parent condition|");
        assertValidEdges(diagram);
    }

    @Test
    void exportsPromisedProxyCycles() throws Exception {
        compile("true", """
            @KoraApp
            public interface App {
                interface First {}
                interface Second {}
                class Both implements First, Second {}
                default First first(Second second) { return new Both(); }
                default Second second(First first) { return new Both(); }
                @Root default String root(First first, Second second) { return "test"; }
            }
            """);
        assertThat(diagram("App")).contains("-->|PromiseOf|");
        assertValidEdges(diagram("App"));
    }

    @Test
    void createsSeparateFilesForEachApplication() throws Exception {
        compile("true", """
            @KoraApp
            public interface App {
                @Root default String root() { return "test"; }
            }
            """, """
            @KoraApp
            public interface OtherApp {
                @Root default java.util.List<String> root() { return java.util.List.of(); }
            }
            """);
        assertThat(diagram("App")).contains("java.lang.String").doesNotContain("java.util.List");
        assertThat(diagram("OtherApp")).contains("java.util.List#lt;java.lang.String#gt;");
        assertThat(directory.resolve("classes/example/OtherAppGraph.md")).exists();
    }

    private void compile(String option, String... sources) throws Exception {
        var compilation = new JavaCompilation()
            .withProcessor(new KoraAppProcessor())
            .withClassesDir(directory.resolve("classes"))
            .withGeneratedSourcesDir(directory.resolve("generated"));
        if (option != null) {
            compilation.withOption("-A" + MermaidGraphGenerator.OPTION_ENABLED + "=" + option);
        }
        for (var source : sources) {
            var name = Pattern.compile("public interface (\\w+)").matcher(source);
            assertThat(name.find()).isTrue();
            var path = directory.resolve(name.group(1) + ".java");
            Files.writeString(path, """
                package example;
                import io.koraframework.common.*;
                import io.koraframework.common.annotation.*;
                import io.koraframework.application.graph.*;
                import org.jspecify.annotations.Nullable;
                """ + source);
            compilation.withSources(path);
        }
        var classLoader = compilation.compile();
        if (classLoader instanceof AutoCloseable closeable) {
            closeable.close();
        }
        assertThat(compilation.diagnostics()).noneMatch(d -> d.getMessage(null).contains("not recognized"));
    }

    private String diagram(String app) throws Exception {
        return Files.readString(directory.resolve("classes/example/" + app + "Graph.mmd"));
    }

    private static String node(String diagram, String label) {
        return diagram.lines().filter(line -> line.contains("[\"" + label + "\"]"))
            .map(line -> line.trim().substring(0, line.trim().indexOf('['))).findFirst().orElseThrow();
    }

    private static void assertValidEdges(String diagram) {
        var nodes = new HashSet<String>();
        var nodeMatcher = Pattern.compile("(?m)^    (component\\d+)\\[").matcher(diagram);
        while (nodeMatcher.find()) nodes.add(nodeMatcher.group(1));
        var edgeMatcher = Pattern.compile("(?m)^    (component\\d+) -->\\|[^|]+\\| (component\\d+)$").matcher(diagram);
        while (edgeMatcher.find()) {
            assertThat(nodes).contains(edgeMatcher.group(1), edgeMatcher.group(2));
        }
    }
}

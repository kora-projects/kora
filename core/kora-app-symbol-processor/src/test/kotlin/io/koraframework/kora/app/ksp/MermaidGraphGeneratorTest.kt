package io.koraframework.kora.app.ksp

import io.koraframework.ksp.common.KotlinCompilation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class MermaidGraphGeneratorTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["false", "invalid"])
    fun disabledUnlessEnabled(option: String?) {
        val resources = compile(option, """
            @KoraApp
            interface App {
                @Root fun root() = "test"
            }
        """)
        assertThat(resources.resolve("example/AppGraph.mmd")).doesNotExist()
        assertThat(resources.resolve("example/AppGraph.md")).doesNotExist()
        assertThat(directory.resolve("generated/example/AppGraph.kt")).exists()
    }

    @Test
    fun exportsDependenciesTagsAndInterceptors() {
        val resources = compile("true", """
            @KoraApp
            interface App {
                class Item
                class Marker
                class RootType
                class WrappedValue
                class Wrapper : Wrapped<WrappedValue> {
                    override fun value() = WrappedValue()
                }
                class Interceptor : GraphInterceptor<Item> {
                    override fun afterInit(value: Item) = value
                    override fun beforeRelease(value: Item) = value
                }
                interface Missing
                fun item() = Item()
                @Tag(Marker::class) fun taggedItem() = Item()
                fun strings(): List<String> = listOf()
                fun interceptor() = Interceptor()
                fun wrapper() = Wrapper()
                @Root fun root(item: Item, value: ValueOf<Item>, promise: PromiseOf<Item>, node: Node<Item>,
                    all: All<Item>, allValues: All<ValueOf<Item>>, allPromises: All<PromiseOf<Item>>,
                    @Tag(Marker::class) tagged: Item, strings: List<String>, missing: Missing?,
                    type: TypeRef<Item>, graph: Graph, wrapped: WrappedValue) = RootType()
            }
        """)
        val diagram = diagram(resources)
        assertThat(diagram).startsWith("flowchart TD\n")
            .contains("kotlin.collections.List#lt;kotlin.String#gt;", "@Tag(example.App.Marker)")
            .contains("-->|dependency|", "-->|ValueOf|", "-->|PromiseOf|", "-->|Node|",
                "-->|All dependency|", "-->|All ValueOf|", "-->|All PromiseOf|", "-->|interceptor|", "-->|Wrapped|")
            .doesNotContain("Missing", "TypeRef", "kotlin.collections.List<kotlin.String>")
        assertThat(diagram).contains("${node(diagram, "example.App.RootType")} -->|dependency| ${node(diagram, "example.App.Item")}")
        assertThat(Files.readString(resources.resolve("example/AppGraph.md")))
            .isEqualTo("```mermaid\n$diagram```\n")
        assertValidEdges(diagram)
    }

    @Test
    fun exportsConditionalAlternatives() {
        val resources = compile("true", """
            @KoraApp
            interface App {
                interface Choice
                class First : Choice
                class Second : Choice
                class CheckFirst
                class CheckSecond
                class RootType
                class Leaf
                fun leaf() = Leaf()
                @Tag(CheckFirst::class) fun firstCondition(): GraphCondition =
                    GraphCondition { GraphCondition.ConditionResult.Matched("first") }
                @Tag(CheckSecond::class) fun secondCondition(): GraphCondition =
                    GraphCondition { GraphCondition.ConditionResult.Matched("second") }
                @Conditional(tag = CheckFirst::class) fun first(leaf: Leaf) = First()
                @Conditional(tag = CheckSecond::class) fun second() = Second()
                @Root fun root(choice: Choice) = RootType()
            }
        """)
        val diagram = diagram(resources)
        val root = node(diagram, "example.App.RootType")
        assertThat(diagram)
            .contains("$root -->|OneOf dependency| ${node(diagram, "example.App.First")}")
            .contains("$root -->|OneOf dependency| ${node(diagram, "example.App.Second")}")
            .contains("-->|condition|", "-->|parent condition|")
        assertValidEdges(diagram)
    }

    @Test
    fun exportsPromisedProxyCycles() {
        val resources = compile("true", """
            @KoraApp
            interface App {
                interface First
                interface Second
                class Both : First, Second
                fun first(second: Second): First = Both()
                fun second(first: First): Second = Both()
                @Root fun root(first: First, second: Second) = "test"
            }
        """)
        assertThat(diagram(resources)).contains("-->|PromiseOf|")
        assertValidEdges(diagram(resources))
    }

    @Test
    fun createsSeparateFilesForEachApplication() {
        val resources = compile("true", """
            @KoraApp
            interface App {
                @Root fun root() = "test"
            }
        """, """
            @KoraApp
            interface OtherApp {
                @Root fun root(): List<String> = listOf()
            }
        """)
        assertThat(diagram(resources)).contains("kotlin.String").doesNotContain("kotlin.collections.List")
        assertThat(diagram(resources, "OtherApp")).contains("kotlin.collections.List#lt;kotlin.String#gt;")
        assertThat(resources.resolve("example/OtherAppGraph.md")).exists()
    }

    private fun compile(option: String?, vararg sources: String): Path {
        val compilation = KotlinCompilation().withProcessor(KoraAppProcessorProvider())
            .withGeneratedSourcesDir(directory.resolve("generated"))
        option?.let { compilation.processorsOptions[MermaidGraphGenerator.OPTION_ENABLED] = it }
        for (source in sources) {
            val name = Regex("interface (\\w+)").find(source)!!.groupValues[1]
            val path = directory.resolve("$name.kt")
            Files.writeString(path, """
                package example
                import io.koraframework.common.*
                import io.koraframework.common.annotation.*
                import io.koraframework.application.graph.*
            """.trimIndent() + "\n" + source.trimIndent())
            compilation.withSrc(path)
        }
        val classLoader = compilation.compile()
        if (classLoader is AutoCloseable) classLoader.close()
        return compilation.baseDir.resolve("resourceOutputDir")
    }

    private fun diagram(resources: Path, app: String = "App") = Files.readString(resources.resolve("example/${app}Graph.mmd"))

    private fun node(diagram: String, label: String) = diagram.lineSequence()
        .first { it.contains("[\"$label\"]") }.trim().substringBefore('[')

    private fun assertValidEdges(diagram: String) {
        val nodes = Regex("(?m)^    (component\\d+)\\[").findAll(diagram).map { it.groupValues[1] }.toSet()
        for (edge in Regex("(?m)^    (component\\d+) -->\\|[^|]+\\| (component\\d+)$").findAll(diagram)) {
            assertThat(nodes).contains(edge.groupValues[1], edge.groupValues[2])
        }
    }
}

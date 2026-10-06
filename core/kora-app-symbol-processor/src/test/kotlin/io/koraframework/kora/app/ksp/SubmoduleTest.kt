package io.koraframework.kora.app.ksp

import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.ksp.common.KotlinCompilation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

/**
 * The @KoraSubmodule is compiled on its own, like a separate Gradle module, and the @KoraApp is compiled against its classes.
 */
class SubmoduleTest {
    private val imports = """
        import io.koraframework.common.annotation.*
        import io.koraframework.application.graph.*

    """.trimIndent()

    private val submodule = "@KoraSubmodule\ninterface Feature"

    private val offCondition = """
        class OffCondition : GraphCondition {
            override fun eval(): GraphCondition.ConditionResult = GraphCondition.ConditionResult.Failed("off")
        }
    """.trimIndent()

    @Test
    fun conditionalComponent(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, offCondition, """
            @Component
            @Conditional(tag = OffCondition::class)
            class DisabledService
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Tag(OffCondition::class)
                fun off(): GraphCondition = OffCondition()
                @Root
                fun root(s: DisabledService?): String = s.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("null")
    }

    @Test
    fun conditionalModuleFunction(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, offCondition, """
            @Module
            interface FeatureModule {
                @Conditional(tag = OffCondition::class)
                fun disabled(): Long = 1L
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Tag(OffCondition::class)
                fun off(): GraphCondition = OffCondition()
                @Root
                fun root(l: Long?): String = l.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("null")
    }

    @Test
    fun defaultComponentClassCanBeOverridden(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            interface Greeter { fun hi(): String }
            @DefaultComponent
            @Component
            class DefaultGreeter : Greeter { override fun hi() = "default" }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                fun custom(): Greeter = object : Greeter { override fun hi() = "custom" }
                @Root
                fun root(g: Greeter): String = g.hi()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("custom")
    }

    @Test
    fun factoryModuleFunction(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            class InnerModule {
                fun l(): Long = 7L
            }
            @Module
            interface FeatureModule {
                @FactoryModule
                fun inner(): InnerModule = InnerModule()
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Root
                fun root(l: Long): String = l.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("7")
    }

    @Test
    fun inheritedModuleFunctions(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            interface BaseModule {
                fun base(): Long = 7L
                fun overridden(): Int = 1
            }
            @Module
            interface FeatureModule : BaseModule {
                override fun overridden(): Int = 8
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Root
                fun root(l: Long, i: Int): String = "${'$'}l:${'$'}i"
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("7:8")
    }

    @Test
    fun genericComponent(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            @Component
            class GenericRepo<T>(val name: Long)
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                fun l(): Long = 7L
                @Root
                fun root(r: GenericRepo<Int>): String = r.name.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("7")
    }

    @Test
    fun genericModuleFunction(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            class Holder<T>(val value: T)
            @Module
            interface FeatureModule {
                fun <T> holder(value: T): Holder<T> = Holder(value)
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                fun l(): Long = 7L
                @Root
                fun root(h: Holder<Long>): String = h.value.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("7")
    }

    @Test
    fun privateModuleFunction(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            @Module
            interface FeatureModule {
                fun svc(): Long = prefix().toLong()
                private fun prefix(): String = "7"
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Root
                fun root(l: Long): String = l.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("7")
    }

    @Test
    fun submoduleModuleExtendsAnnotatedModule(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            @Module
            interface ParentModule {
                fun l(): Long = 5L
            }
            """.trimIndent(), """
            @Module
            interface ChildModule : ParentModule {
                fun i(): Int = 1
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Root
                fun root(l: Long, i: Int): String = l.toString() + i
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("51")
    }

    @Test
    fun submoduleModuleExtendsGenericInterfaceUnused(info: TestInfo) {
        val root = compileAndGetRoot(
            info, submodule, """
            interface Base<T> {
                fun list(): List<T> = listOf()
            }
            """.trimIndent(), """
            @Module
            interface ChildModule : Base<String> {
                fun i(): Int = 1
            }
            """.trimIndent(),
            app = """
            @KoraApp
            interface ExampleApplication : Feature {
                @Root
                fun root(i: Int): String = i.toString()
            }
            """.trimIndent()
        )
        assertThat(root).isEqualTo("1")
    }

    private fun compileAndGetRoot(info: TestInfo, vararg submoduleSources: String, app: String): Any? {
        val name = info.testMethod.get().name
        val pkg = "submoduletest.$name"
        val dir = Path.of("build/in-test-generated-ksp/submodule/$name").toAbsolutePath()
        dir.toFile().deleteRecursively()
        val submoduleCompilation = KotlinCompilation()
            .withProcessors(listOf(KoraAppProcessorProvider(), KoraSubmoduleProcessorProvider()))
            .withGeneratedSourcesDir(dir.resolve("sub-gen"))
            .withSrc(submoduleSources.mapIndexed { i, source -> write(dir.resolve("sub-src"), pkg, "Submodule$i", source) })
        submoduleCompilation.compile()
        val appCompilation = KotlinCompilation()
            .withProcessors(listOf(KoraAppProcessorProvider(), KoraSubmoduleProcessorProvider()))
            .withGeneratedSourcesDir(dir.resolve("app-gen"))
            .withSrc(write(dir.resolve("app-src"), pkg, "ExampleApplication", app))
            .apply { classpathEntries.add(submoduleCompilation.classOutputDir) }
        appCompilation.compile()
        val cl = URLClassLoader(
            arrayOf(appCompilation.classOutputDir.toUri().toURL(), submoduleCompilation.classOutputDir.toUri().toURL()),
            Thread.currentThread().contextClassLoader
        )
        @Suppress("UNCHECKED_CAST")
        val draw = (cl.loadClass("$pkg.ExampleApplicationGraph").getConstructor().newInstance() as Supplier<ApplicationGraphDraw>).get()
        val graph = draw.init()
        return graph.get(draw.nodes.first { it.type() == String::class.java })
    }

    private fun write(dir: Path, pkg: String, name: String, source: String): Path {
        val file = dir.resolve(pkg.replace('.', '/')).resolve("$name.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "package $pkg\n$imports\n$source")
        return file
    }
}

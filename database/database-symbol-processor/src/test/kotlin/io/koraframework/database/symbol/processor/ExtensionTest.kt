@file:OptIn(KspExperimental::class)

package io.koraframework.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.common.Tag
import io.koraframework.database.symbol.processor.app.TestKoraApp
import io.koraframework.database.symbol.processor.app.TestKoraAppTagged
import io.koraframework.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.GraphUtil.toGraph
import io.koraframework.ksp.common.symbolProcess
import java.lang.reflect.Constructor
import java.util.function.Supplier

class ExtensionTest : AbstractJdbcRepositoryTest() {

    @Test
    fun test() {
        val classLoader = symbolProcess(listOf(KoraAppProcessorProvider(), RepositorySymbolProcessorProvider()), listOf(TestKoraApp::class))
        val clazz = classLoader.loadClass(TestKoraApp::class.qualifiedName + "Graph")
        val constructors = clazz.constructors as Array<Constructor<out Supplier<out ApplicationGraphDraw>>>
        val graphDraw: ApplicationGraphDraw = constructors[0].newInstance().get()
        Assertions.assertThat(graphDraw).isNotNull
        Assertions.assertThat(graphDraw.size()).isEqualTo(3)
    }

    @Test
    fun testTagged() {
        val classLoader = symbolProcess(listOf(KoraAppProcessorProvider(), RepositorySymbolProcessorProvider()), listOf(TestKoraAppTagged::class))
        val clazz = classLoader.loadClass("io.koraframework.database.symbol.processor.app.\$TestKoraAppTagged_TestRepository_Impl")
        val constructor = clazz.constructors[0]
        constructor.parameters.forEach { p ->
            Assertions.assertThat(p.isAnnotationPresent(Tag::class.java)).isTrue
            val annotation = p.getAnnotation(Tag::class.java)
            Assertions.assertThat(annotation.value).isEqualTo(TestKoraAppTagged.ExampleTag::class)
        }
    }


    @Test
    fun testTaggedRepo() {
        val result = compile0(listOf(KoraAppProcessorProvider(), RepositorySymbolProcessorProvider()),
            """
            import org.mockito.Mockito

            @KoraApp
            interface Application {
                @Root
                fun testRoot(
                    @Tag(value = TestRepository::class)
                    repo: TestRepository
                ) = repo.test()
        
                fun jdbcQueryExecutorAccessor(): JdbcConnectionFactory {
                    return Mockito.mock(JdbcConnectionFactory::class.java)
                }
            }
            """.trimIndent(),
            """
            @Repository
            @Tag(value = TestRepository::class)
            interface TestRepository : JdbcRepository {
                @Query("SELECT 1;")
                fun select1()
                
                fun test() = "i'm in test repo"
            }
            """.trimIndent(),
        )

        result.assertSuccess()

        val graph = loadClass("ApplicationGraph").toGraph()

        val testRoot = graph.graph.get(graph.draw.findNodeByType(String::class.java))

        Assertions.assertThat(testRoot)
            .isEqualTo("i'm in test repo")
    }
}

@file:OptIn(KspExperimental::class)

package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.database.symbol.processor.app.TestKoraApp
import ru.tinkoff.kora.database.symbol.processor.app.TestKoraAppTagged
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.reflect.Constructor
import java.util.function.Supplier
import kotlin.reflect.full.isSubclassOf
import ru.tinkoff.kora.database.symbol.processor.jdbc.AbstractJdbcRepositoryTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

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
        val clazz = classLoader.loadClass("ru.tinkoff.kora.database.symbol.processor.app.\$TestKoraAppTagged_TestRepository_Impl")
        val constructor = clazz.constructors[0]
        constructor.parameters.forEach { p ->
            Assertions.assertThat(p.isAnnotationPresent(Tag::class.java)).isTrue
            val annotation = p.getAnnotation(Tag::class.java)
            Assertions.assertThat(annotation.value).hasSize(1)
            Assertions.assertThat(annotation.value[0].isSubclassOf(TestKoraAppTagged.ExampleTag::class)).isTrue()
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
                    @Tag(value = [TestRepository::class, JdbcRepository::class, Int::class])
                    repo: TestRepository
                ) = repo.test()
        
                fun jdbcQueryExecutorAccessor(): JdbcConnectionFactory {
                    return Mockito.mock(JdbcConnectionFactory::class.java)
                }
            }
            """.trimIndent(),
            """
            @Repository
            @Tag(value = [TestRepository::class, JdbcRepository::class, Int::class])
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

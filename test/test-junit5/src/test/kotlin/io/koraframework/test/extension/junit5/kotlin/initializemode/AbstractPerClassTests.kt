package io.koraframework.test.extension.junit5.kotlin.initializemode

import io.koraframework.test.extension.junit5.KoraAppGraph
import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.testdata.TestApplication
import io.koraframework.test.extension.junit5.testdata.TestComponent1
import io.koraframework.test.extension.junit5.testdata.TestComponent12
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertSame
import kotlin.concurrent.Volatile

@KoraAppTest(value = TestApplication::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractPerClassTests {

    @TestComponent
    lateinit var component1: TestComponent1

    @TestComponent
    lateinit var component12: TestComponent12

    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    class TestPerClassChild : AbstractPerClassTests() {

        @Test
        @Order(1)
        fun test1(graph: KoraAppGraph) {
            assertNotNull(component1)
            assertNotNull(component12)

            assertNull(prevGraph)
            assertNotNull(graph)
            prevGraph = graph
        }

        @Test
        @Order(2)
        fun test2(graph: KoraAppGraph) {
            assertNotNull(component1)
            assertNotNull(component12)

            assertNotNull(prevGraph)
            assertNotNull(graph)
            assertSame(graph, prevGraph)
        }
    }

    companion object {
        @Volatile
        var prevGraph: KoraAppGraph? = null
    }
}


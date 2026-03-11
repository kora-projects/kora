package io.koraframework.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.Graph
import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.testdata.TestApplication
import io.koraframework.test.extension.junit5.testdata.TestComponent333
import io.koraframework.test.extension.junit5.testdata.TestComponent3333

@KoraAppTest(TestApplication::class)
class MockkNodeWithSubnodesWithRelaxUnitFunTests {

    @field:MockK(relaxUnitFun = true)
    @TestComponent
    private lateinit var mock: TestComponent333

    @TestComponent
    private lateinit var bean: TestComponent3333

    @BeforeEach
    fun setupMocks() {
        every { mock.get() } returns "??"
    }

    @Test
    fun fieldMocked(graph: Graph) {
        Assertions.assertEquals("??", mock.get())
        Assertions.assertEquals(2, graph.draw().size())
    }

    @Test
    fun fieldMockedAndInBeanDependency(graph: Graph) {
        Assertions.assertEquals("??", mock.get())
        Assertions.assertEquals("??3", bean.get())
        Assertions.assertEquals(2, graph.draw().size())
    }
}

package io.koraframework.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.SpyK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.testdata.TestApplication
import io.koraframework.test.extension.junit5.testdata.TestComponent1
import io.koraframework.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class SpykFromFieldTests {

    @field:SpyK
    @TestComponent
    var spy: TestComponent1 = TestComponent1()

    @TestComponent
    lateinit var bean: TestComponent12

    @BeforeEach
    fun setupMocks() {
        every { spy.get() } returns "?"
    }

    @Test
    fun fieldMocked() {
        assertEquals("?", spy.get())
    }

    @Test
    fun fieldMockedAndInBeanDependency() {
        assertEquals("?", spy.get())
        assertEquals("?2", bean.get())
    }
}

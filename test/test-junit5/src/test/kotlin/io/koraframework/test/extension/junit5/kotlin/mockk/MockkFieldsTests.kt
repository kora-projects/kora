package io.koraframework.test.extension.junit5.kotlin.mockk

import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.koraframework.common.Tag
import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.testdata.LifecycleComponent
import io.koraframework.test.extension.junit5.testdata.TestApplication
import io.koraframework.test.extension.junit5.testdata.TestComponent1
import io.koraframework.test.extension.junit5.testdata.TestComponent12
import io.koraframework.test.extension.junit5.testdata.TestComponent2
import io.koraframework.test.extension.junit5.testdata.TestComponent23

@KoraAppTest(TestApplication::class, components = [TestComponent23::class])
class MockkFieldsTests {

    @field:MockK
    @TestComponent
    lateinit var mock: TestComponent1

    @MockK
    @Tag(LifecycleComponent::class)
    @TestComponent
    lateinit var mock2: TestComponent2

    @TestComponent
    lateinit var bean: TestComponent12

    @BeforeEach
    fun setupMocks() {
        every(fun MockKMatcherScope.(): String? {
            return mock.get()
        }) returns "?"
        every { mock2.get() } returns "999"
    }

    @Test
    fun fieldMocked() {
        assertEquals("?", mock.get())
    }

    @Test
    fun mockkOnProperty() {
        assertEquals("999", mock2.get())
    }

    @Test
    fun fieldMockedAndInBeanDependency() {
        assertEquals("?", mock.get())
        assertEquals("?2", bean.get())
    }
}

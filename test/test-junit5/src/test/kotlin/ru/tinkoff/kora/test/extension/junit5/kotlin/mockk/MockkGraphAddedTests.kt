package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.*

@KoraAppTest(value = TestApplication::class, components = [TestComponent12::class, TestComponent23::class])
class MockkGraphAddedTests {

    @Test
    fun mock(@MockK @TestComponent mock: TestComponent1) {
        every { mock.get() } returns "?"
        assertEquals("?", mock.get())
    }

    @Test
    fun mockWithTag(
        @Tag(LifecycleComponent::class)
        @MockK @TestComponent mock: TestComponent2
    ) {
        every { mock.get() } returns "?"
        assertEquals("?", mock.get())
    }

    @Test
    fun beanWithTaggedMock(
        @Tag(LifecycleComponent::class)
        @MockK @TestComponent mock: TestComponent2,
        @TestComponent component23: TestComponent23
    ) {
        every { mock.get() } returns "?"
        assertEquals("?", mock.get())
        assertEquals("?3", component23.get())
    }
}

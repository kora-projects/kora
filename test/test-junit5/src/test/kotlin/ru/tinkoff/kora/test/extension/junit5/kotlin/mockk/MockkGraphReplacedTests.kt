package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.*

@KoraAppTest(value = TestApplication::class, components = [TestComponent12::class, TestComponent23::class])
class MockkGraphReplacedTests {

    @Test
    fun mock(@MockK @TestComponent mock: TestComponent1) {
        every { mock.get() } returns "?"
        Assertions.assertEquals("?", mock.get())
    }

    @Test
    fun mockWithTag(@MockK @TestComponent @Tag(LifecycleComponent::class) mock: TestComponent2) {
        every { mock.get() } returns "?"
        Assertions.assertEquals("?", mock.get())
    }

    @Test
    fun beanWithTaggedMock(
        @MockK @TestComponent @Tag(LifecycleComponent::class) mock: TestComponent2,
        @TestComponent component23: TestComponent23
    ) {
        every { mock.get() } returns "?"
        Assertions.assertEquals("?", mock.get())
        Assertions.assertEquals("?3", component23.get())
    }
}

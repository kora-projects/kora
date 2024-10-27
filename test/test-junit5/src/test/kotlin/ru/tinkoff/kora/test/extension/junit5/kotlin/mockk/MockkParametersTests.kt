package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(value = TestApplication::class, components = [TestComponent12::class])
class MockkParametersTests {

    @Test
    fun mock(@MockK @TestComponent mock: TestComponent1) {
        every { mock.get() } returns "?"
        Assertions.assertEquals("?", mock.get())
    }

    @Test
    fun beanWithMock(
        @MockK @TestComponent mock: TestComponent1,
        @TestComponent bean: TestComponent12
    ) {
        every { mock.get() } returns "?"
        Assertions.assertEquals("?", mock.get())
        Assertions.assertEquals("?2", bean.get())
    }
}

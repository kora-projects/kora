package ru.tinkoff.kora.test.extension.junit5.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class MockkConstructorTests(
    @MockK @TestComponent val mock: TestComponent1,
    @TestComponent val bean: TestComponent12
) {

    @BeforeEach
    fun setupMocks() {
        every { mock.get() } returns "?"
    }

    @Test
    fun fieldMocked() {
        assertEquals("?", mock.get())
    }

    @Test
    fun fieldMockedAndInBeanDependency() {
        assertEquals("?", mock.get())
        assertEquals("?2", bean.get())
    }

}

package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class MockkRelaxTests(
    @MockK(relaxed = true) @TestComponent val mock: TestComponent1,
    @TestComponent val bean: TestComponent12
) {

    @Test
    fun fieldMocked() {
        assertEquals("", mock.get())
    }

    @Test
    fun fieldMockedAndInBeanDependency() {
        assertEquals("", mock.get())
        assertEquals("2", bean.get())
    }

}

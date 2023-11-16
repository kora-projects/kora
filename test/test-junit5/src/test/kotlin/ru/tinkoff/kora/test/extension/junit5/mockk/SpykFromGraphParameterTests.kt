package ru.tinkoff.kora.test.extension.junit5.mockk

import io.mockk.every
import io.mockk.impl.annotations.SpyK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Spy
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class SpykFromGraphParameterTests {

    @Test
    fun parameterSpy(@SpyK @TestComponent spy: TestComponent12) {
        every { spy.get() } returns "?"
        assertEquals("?", spy.get())
    }
}

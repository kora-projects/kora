package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.SpyK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class SpykFromGraphRootTests {

    @field:SpyK
    @TestComponent
    lateinit var spy: TestComponent12

    @BeforeEach
    fun setupSpy() {
        every { spy.get() } returns "?"
    }

    @Test
    fun fieldSpy() {
        assertEquals("?", spy.get())
    }
}

package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.SpyK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class SpykFromGraphTests {

    @field:SpyK
    @TestComponent
    lateinit var spy: TestComponent1

    @TestComponent
    lateinit var bean: TestComponent12

    @BeforeEach
    fun setupSpy() {
        every { spy.get() } returns "?"
    }

    @Test
    fun fieldSpy() {
        assertEquals("?", spy.get())
    }

    @Test
    fun fieldSpyAndBeanDependency() {
        assertEquals("?", spy.get())
        assertEquals("?2", bean.get())
    }
}

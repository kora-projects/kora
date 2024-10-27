package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(TestApplication::class)
class MockkBeforeEachAfterEachParameterTests {

    @TestComponent
    lateinit var bean: TestComponent12

    @BeforeEach
    fun setupMocks(@MockK @TestComponent mock: TestComponent1) {
        every { mock.get() } returns "?"
    }

    @AfterEach
    fun checkMocks(@MockK @TestComponent mock: TestComponent1) {
        assertNotNull(mock.get())
    }

    @Test
    fun fieldMocked(@MockK @TestComponent mock: TestComponent1) {
        assertEquals("?", mock.get())
    }

    @Test
    fun fieldMockedAndInBeanDependency(@MockK @TestComponent mock: TestComponent1) {
        assertEquals("?", mock.get())
        assertEquals("?2", bean!!.get())
    }
}

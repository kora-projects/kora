package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.isMockKMock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication

@KoraAppTest(TestApplication::class)
class MockWrappedParentTests(
    @MockK @TestComponent val someWrappedInterface: TestApplication.SomeWrapped,
    @TestComponent val someContainer: TestApplication.SomeContainer,
    @MockK @TestComponent val someChildInterface: TestApplication.SomeChild,
    @MockK @TestComponent val customWrapper: TestApplication.CustomWrapper
) {

    @Test
    fun wrappedMocked() {
        assertTrue(isMockKMock(someChildInterface))
        every { someWrappedInterface.toString() } returns ("12345")
        assertNotNull(customWrapper)

        assertSame(someWrappedInterface, someContainer.wrapped())

        assertEquals("12345", someWrappedInterface.toString())
        assertEquals("12345", someContainer.wrapped().toString())
    }
}

package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication.CustomWrapper

@KoraAppTest(TestApplication::class)
class MockWrappedTests(
    @TestComponent val someContainer: TestApplication.SomeContainer,
    @MockK @TestComponent val someChild: TestApplication.SomeChild,
    @MockK @TestComponent val someWrapped: TestApplication.SomeWrapped,
    @MockK @TestComponent val someContract: TestApplication.SomeContract
) {

    @Test
    fun wrappedMocked() {
        assertNotNull(someContainer)
        assertNotNull(someChild)
        assertNotNull(someWrapped)
        assertNotNull(someContract)
    }

    @Test
    fun wrappedMockedValue(@TestComponent wrapper: CustomWrapper) {
        assertSame(someContract, wrapper.value())
    }

    @Test
    fun wrappedMockedWrapperMockedValue(@MockK @TestComponent wrapper: CustomWrapper) {
        assertSame(someContract, wrapper.value())
    }
}

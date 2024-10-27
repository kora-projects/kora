package ru.tinkoff.kora.test.extension.junit5.kotlin.initializemode

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12
import kotlin.concurrent.Volatile

@KoraAppTest(value = TestApplication::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MethodPerClassTests {

    @Test
    @Order(1)
    fun test1(@TestComponent component1: TestComponent1?, @TestComponent component12: TestComponent12?) {
        assertNotNull(component1)
        assertNotNull(component12)
        prevComponent1 = component1
        prevComponent12 = component12
    }

    @Test
    @Order(2)
    fun test2(@TestComponent component1: TestComponent1?, @TestComponent component12: TestComponent12?) {
        assertNotNull(component1)
        assertNotNull(component12)
        assertSame(prevComponent1, component1)
        assertSame(prevComponent12, component12)
    }

    companion object {
        @Volatile
        var prevComponent1: TestComponent1? = null

        @Volatile
        var prevComponent12: TestComponent12? = null
    }
}

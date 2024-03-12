package ru.tinkoff.kora.test.extension.junit5.initializemodek

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12
import kotlin.concurrent.Volatile

@KoraAppTest(value = TestApplication::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConstructorPerClassTests(
    @TestComponent private val component1: TestComponent1,
    @TestComponent private val component12: TestComponent12
) {

    @Test
    @Order(1)
    fun test1() {
        assertNotNull(component1)
        assertNotNull(component12)
        prevComponent1 = component1
        prevComponent12 = component12
    }

    @Test
    @Order(2)
    fun test2() {
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

package io.koraframework.test.extension.junit5.kotlin.initializemode

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.testdata.TestApplication
import io.koraframework.test.extension.junit5.testdata.TestComponent1
import io.koraframework.test.extension.junit5.testdata.TestComponent12

@KoraAppTest(value = TestApplication::class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MethodPerMethodTests {

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
        assertNotSame(FieldPerMethodTests.prevComponent1, component1)
        assertNotSame(FieldPerMethodTests.prevComponent12, component12)
    }

    companion object {
        @Volatile
        var prevComponent1: TestComponent1? = null

        @Volatile
        var prevComponent12: TestComponent12? = null
    }
}

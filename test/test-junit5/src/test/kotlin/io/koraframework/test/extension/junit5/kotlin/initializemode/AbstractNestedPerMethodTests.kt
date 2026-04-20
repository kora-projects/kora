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
abstract class AbstractNestedPerMethodTests {

    companion object {

        @Volatile
        var prevComponent1: TestComponent1? = null
        @Volatile
        var prevComponent12: TestComponent12? = null

        @JvmStatic
        @AfterAll
        fun cleanup() {
            prevComponent1 = null
            prevComponent12 = null
        }
    }

    @TestComponent
    lateinit var component1: TestComponent1
    @TestComponent
    lateinit var component12: TestComponent12

    @Order(1)
    @Nested
    inner class Nested1 {

        @TestComponent
        lateinit var componentNested12: TestComponent12

        @Test
        fun test1() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
        }

        @Test
        fun test2() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
            assertNotSame(prevComponent12, componentNested12)
        }
    }

    @Order(2)
    @Nested
    inner class Nested2 {

        @Test
        fun test3() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
        }

        @Test
        fun test4() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
        }
    }
}


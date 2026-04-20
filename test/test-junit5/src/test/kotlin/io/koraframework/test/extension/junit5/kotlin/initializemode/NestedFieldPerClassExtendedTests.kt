package io.koraframework.test.extension.junit5.kotlin.initializemode

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import io.koraframework.test.extension.junit5.testdata.TestComponent1
import io.koraframework.test.extension.junit5.testdata.TestComponent12

internal class NestedFieldPerClassExtendedTests : AbstractPerClassTests() {

    companion object {
        @Volatile
        var prevComponent1: TestComponent1? = null

        @Volatile
        var prevComponent12: TestComponent12? = null
    }

    @Test
    @Order(3)
    fun test3() {
        assertNotNull(component1)
        assertNotNull(component12)
        prevComponent1 = component1
        prevComponent12 = component12
    }

    @Test
    @Order(4)
    fun test4() {
        assertNotNull(component1)
        assertNotNull(component12)
        assertSame(prevComponent1, component1)
        assertSame(prevComponent12, component12)
    }

    @Order(5)
    @Nested
    inner class Nested5 {

        @Test
        fun test5() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }

        @Test
        fun test6() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }
    }

    @Order(6)
    @Nested
    inner class Nested6 {

        @Test
        fun test7() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }

        @Test
        fun test8() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }
    }
}

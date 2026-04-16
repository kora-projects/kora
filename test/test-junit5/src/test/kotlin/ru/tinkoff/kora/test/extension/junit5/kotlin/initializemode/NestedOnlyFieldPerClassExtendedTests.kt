package ru.tinkoff.kora.test.extension.junit5.kotlin.initializemode

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

internal class NestedOnlyFieldPerClassExtendedTests : AbstractNestedPerClassTests() {

    @Order(3)
    @Nested
    inner class Nested3 {

        @Test
        fun test3() {
            assertNotNull(component1)
            assertNotNull(component12)
            if (prevComponent1 == null) {
                prevComponent1 = component1
            } else {
                assertSame(prevComponent1, component1)
            }
            if (prevComponent12 == null) {
                prevComponent12 = component12
            } else {
                assertSame(prevComponent12, component12)
            }
        }

        @Test
        fun test4() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }
    }

    @Order(4)
    @Nested
    inner class Nested4 {

        @Test
        fun test5() {
            assertNotNull(component1)
            assertNotNull(component12)
            if (prevComponent1 == null) {
                prevComponent1 = component1
            } else {
                assertSame(prevComponent1, component1)
            }
            if (prevComponent12 == null) {
                prevComponent12 = component12
            } else {
                assertSame(prevComponent12, component12)
            }
        }

        @Test
        fun test6() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertSame(prevComponent1, component1)
            assertSame(prevComponent12, component12)
        }
    }
}

package ru.tinkoff.kora.test.extension.junit5.kotlin.initializemode

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12

internal class NestedOnlyFieldPerMethodExtendedTests : AbstractNestedPerMethodTests() {

    @Order(3)
    @Nested
    inner class Nested3 {

        @TestComponent
        lateinit var componentNested12: TestComponent12

        @Test
        fun test3() {
            assertNotNull(component1)
            assertNotNull(component12)
            prevComponent1 = component1
            prevComponent12 = component12
        }

        @Test
        fun test4() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
            assertNotSame(prevComponent12, componentNested12)
        }
    }

    @Order(4)
    @Nested
    inner class Nested4 {

        @Test
        fun test5() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
        }

        @Test
        fun test6() {
            assertNotNull(component1)
            assertNotNull(component12)
            assertNotSame(prevComponent1, component1)
            assertNotSame(prevComponent12, component12)
        }
    }
}

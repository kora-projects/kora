package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class NestedFieldPerMethodExtendedTests extends AbstractNestedPerMethodTests {

    @Test
    @Order(3)
    void test7() {
        assertNotNull(component1);
        assertNotNull(component12);
        prevComponent1 = component1;
        prevComponent12 = component12;
    }

    @Test
    @Order(4)
    void test8() {
        assertNotNull(component1);
        assertNotNull(component12);
        assertNotSame(prevComponent1, component1);
        assertNotSame(prevComponent12, component12);
    }

    @Order(5)
    @Nested
    class Nested5 {

        @TestComponent
        private TestComponent12 componentNested12;

        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
            assertNotSame(prevComponent12, componentNested12);
        }

        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
            assertNotSame(prevComponent12, componentNested12);
        }
    }

    @Order(6)
    @Nested
    class Nested6 {

        @Test
        void test5() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
        }

        @Test
        void test6() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
        }
    }
}

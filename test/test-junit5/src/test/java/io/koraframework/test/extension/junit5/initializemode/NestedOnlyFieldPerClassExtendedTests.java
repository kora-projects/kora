package io.koraframework.test.extension.junit5.initializemode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NestedOnlyFieldPerClassExtendedTests extends AbstractNestedPerClassTests {

    @Nested
    class Nested3 {

        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            if (prevComponent1 == null) {
                prevComponent1 = component1;
            } else {
                assertSame(prevComponent1, component1);
            }
            if (prevComponent12 == null) {
                prevComponent12 = component12;
            } else {
                assertSame(prevComponent12, component12);
            }
        }

        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
        }
    }

    @Nested
    class Nested4 {

        @Test
        void test5() {
            assertNotNull(component1);
            assertNotNull(component12);
            if (prevComponent1 == null) {
                prevComponent1 = component1;
            } else {
                assertSame(prevComponent1, component1);
            }
            if (prevComponent12 == null) {
                prevComponent12 = component12;
            } else {
                assertSame(prevComponent12, component12);
            }
        }

        @Test
        void test6() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
        }
    }
}

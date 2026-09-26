package io.koraframework.test.extension.junit5.initializemode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NestedFieldPerClassExtendedTests extends AbstractNestedPerClassTests {

    @Test
    void test3() {
        assertNotNull(component1);
        assertNotNull(component12);
        prevComponent1Holder.set(component1);
        prevComponent12Holder.set(component12);
    }

    @Test
    void test4() {
        assertNotNull(component1);
        assertNotNull(component12);
        assertSame(prevComponent1Holder.get(), component1);
        assertSame(prevComponent12Holder.get(), component12);
    }

    @Nested
    class Nested5 {

        @Test
        void test5() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }

        @Test
        void test6() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }
    }

    @Nested
    class Nested6 {

        @Test
        void test7() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }

        @Test
        void test8() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }
    }
}

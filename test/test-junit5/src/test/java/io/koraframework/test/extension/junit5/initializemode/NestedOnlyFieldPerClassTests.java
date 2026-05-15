package io.koraframework.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class NestedOnlyFieldPerClassTests {

    static volatile TestComponent1 prevComponent1;
    static volatile TestComponent12 prevComponent12;

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;

    @Nested
    class Nested1 {

        @Order(1)
        @Test
        void test1() {
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

        @Order(2)
        @Test
        void test2() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
        }
    }

    @Nested
    class Nested2 {

        @Order(3)
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

        @Order(4)
        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
        }
    }
}

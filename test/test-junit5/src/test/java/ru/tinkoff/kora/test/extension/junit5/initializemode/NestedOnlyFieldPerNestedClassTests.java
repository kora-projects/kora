package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(value = TestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NestedOnlyFieldPerNestedClassTests {

    static volatile TestComponent1 prevComponent1;
    static volatile TestComponent12 prevComponent12;

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Order(1)
    @Nested
    class Nested1 {

        @TestComponent
        private TestComponent12 componentNested12;

        @Test
        void test1() {
            assertNotNull(component1);
            assertNotNull(component12);
            if (prevComponent1 != null) {
                assertNotSame(prevComponent1, component1);
            }
            prevComponent1 = component1;
            if (prevComponent12 != null) {
                assertNotSame(prevComponent12, component12);
                assertNotSame(prevComponent12, componentNested12);
            }
            prevComponent12 = component12;
        }

        @Test
        void test2() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
            assertSame(prevComponent12, componentNested12);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Order(4)
    @Nested
    class Nested2 {

        @TestComponent
        private TestComponent12 componentNested12;

        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            if (prevComponent1 != null) {
                assertNotSame(prevComponent1, component1);
            }
            prevComponent1 = component1;
            if (prevComponent12 != null) {
                assertNotSame(prevComponent12, component12);
                assertNotSame(prevComponent12, componentNested12);
            }
            prevComponent12 = component12;
        }

        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
        }
    }
}

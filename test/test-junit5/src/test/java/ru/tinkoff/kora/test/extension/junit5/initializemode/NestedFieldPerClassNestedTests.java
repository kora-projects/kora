package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@KoraAppTest(value = TestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NestedFieldPerClassNestedTests {

    static volatile TestComponent1 prevComponent1;
    static volatile TestComponent12 prevComponent12;

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Order(3)
    @Nested
    class Nested1 {

        @TestComponent
        private TestComponent12 componentNested;

        @Order(4)
        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            prevComponent1 = component1;
            prevComponent12 = component12;
            componentNested = component12;
        }

        @Order(5)
        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
            assertSame(prevComponent12, componentNested);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Order(6)
    @Nested
    class Nested2 {

        @TestComponent
        private TestComponent12 componentNested;

        @Order(7)
        @Test
        void test5() {
            assertNotNull(component1);
            assertNotNull(component12);
            prevComponent1 = component1;
            prevComponent12 = component12;
            componentNested = component12;
        }

        @Order(8)
        @Test
        void test6() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1, component1);
            assertSame(prevComponent12, component12);
            assertSame(prevComponent12, componentNested);
        }
    }
}

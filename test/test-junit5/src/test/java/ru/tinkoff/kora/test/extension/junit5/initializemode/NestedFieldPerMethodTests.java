package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NestedFieldPerMethodTests {

    static volatile TestComponent1 prevComponent1;
    static volatile TestComponent12 prevComponent12;

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;

    @Test
    @Order(1)
    void test1() {
        assertNotNull(component1);
        assertNotNull(component12);
        prevComponent1 = component1;
        prevComponent12 = component12;
    }

    @Test
    @Order(2)
    void test2() {
        assertNotNull(component1);
        assertNotNull(component12);
        assertNotSame(prevComponent1, component1);
        assertNotSame(prevComponent12, component12);
    }

    @Order(3)
    @Nested
    class Nested1 {

        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
        }

        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertNotSame(prevComponent1, component1);
            assertNotSame(prevComponent12, component12);
        }
    }

    @Order(4)
    @Nested
    class Nested2 {

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

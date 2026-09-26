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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractNestedPerClassTests {

    static final ThreadLocal<TestComponent1> prevComponent1Holder = new ThreadLocal<>();
    static final ThreadLocal<TestComponent12> prevComponent12Holder = new ThreadLocal<>();

    @TestComponent
    TestComponent1 component1;
    @TestComponent
    TestComponent12 component12;

    @Nested
    class Nested1 {

        @Test
        void test1() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }

        @Test
        void test2() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }
    }

    @Nested
    class Nested2 {

        @Test
        void test3() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }

        @Test
        void test4() {
            assertNotNull(component1);
            assertNotNull(component12);
            assertSame(prevComponent1Holder.get(), component1);
            assertSame(prevComponent12Holder.get(), component12);
        }
    }

    @AfterAll
    static void cleanup() {
        prevComponent1Holder.remove();
        prevComponent12Holder.remove();
    }
}


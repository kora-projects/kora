package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(value = TestApplication.class, components = TestComponent12.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractPerClassTests {

    static volatile KoraAppGraph prevGraph = null;

    @TestComponent
    protected TestComponent1 component1;
    @TestComponent
    protected TestComponent12 component12;

    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    final static class TestPerClassChild extends AbstractPerClassTests {

        @Test
        @Order(1)
        void test1(KoraAppGraph graph) {
            assertNotNull(component1);
            assertNotNull(component12);

            assertNull(prevGraph);
            assertNotNull(graph);
            prevGraph = graph;
        }

        @Test
        @Order(2)
        void test2(KoraAppGraph graph) {
            assertNotNull(component1);
            assertNotNull(component12);

            assertNotNull(prevGraph);
            assertNotNull(graph);
            assertSame(graph, prevGraph);
        }
    }
}


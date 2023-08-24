package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(value = TestApplication.class, components = TestComponent12.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerMethodTests {

    static volatile KoraAppGraph prevGraph = null;

    @Test
    @Order(1)
    void test1(KoraAppGraph graph) {
        assertNull(prevGraph);
        assertNotNull(graph);
        prevGraph = graph;
    }

    @Test
    @Order(2)
    void test2(KoraAppGraph graph) {
        assertNotNull(prevGraph);
        assertNotNull(graph);
        assertNotSame(graph, prevGraph);
    }
}

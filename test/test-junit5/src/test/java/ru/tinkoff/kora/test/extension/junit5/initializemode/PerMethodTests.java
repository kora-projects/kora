package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.*;

// Per Method by Default
@KoraAppTest(TestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerMethodTests {

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

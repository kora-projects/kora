package io.koraframework.test.extension.junit5.initializemode;

import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractPerMethodTests {

    static volatile KoraAppGraph prevGraph = null;

    @TestComponent
    protected TestComponent1 component1;
    @TestComponent
    protected TestComponent12 component12;

    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    final static class TestPerMethodChild extends AbstractPerMethodTests {

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
            assertNotSame(graph, prevGraph);
        }
    }
}

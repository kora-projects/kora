package io.koraframework.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.GenericComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestApplication.CustomWrapper;
import io.koraframework.test.extension.junit5.testdata.TestComponent3;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(TestApplication.class)
public class MockAndFullGraphTests {

    @TestComponent
    private KoraAppGraph graph;

    @Mock
    @TestComponent
    private TestComponent3 mock;

    @Test
    void graphIsFull() {
        assertNotNull(graph.getFirst(TypeRef.of(GenericComponent.class, String.class)));
        assertNotNull(graph.getFirst(CustomWrapper.class));
    }
}

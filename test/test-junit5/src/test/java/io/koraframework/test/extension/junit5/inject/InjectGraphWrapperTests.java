package io.koraframework.test.extension.junit5.inject;

import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(TestApplication.class)
public class InjectGraphWrapperTests {

    @Test
    void testWrapped(KoraAppGraph graph) {
        assertNotNull(graph.getFirst(TestApplication.SomeChild.class));
        assertNotNull(graph.getFirst(TestApplication.ComplexWrapped.class));
        assertNotNull(graph.getFirst(Integer.class));
    }
}

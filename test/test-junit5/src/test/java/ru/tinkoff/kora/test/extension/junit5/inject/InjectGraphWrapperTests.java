package ru.tinkoff.kora.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

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

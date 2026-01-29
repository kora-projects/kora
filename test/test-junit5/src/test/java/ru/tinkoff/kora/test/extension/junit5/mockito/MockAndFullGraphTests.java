package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.GenericComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication.CustomWrapper;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplicationOps;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent3;

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

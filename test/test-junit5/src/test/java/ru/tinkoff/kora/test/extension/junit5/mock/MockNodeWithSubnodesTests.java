package ru.tinkoff.kora.test.extension.junit5.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.MockComponent;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent333;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent3333;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(TestApplication.class)
public class MockNodeWithSubnodesTests {

    @MockComponent
    private TestComponent333 mock;
    @TestComponent
    private TestComponent3333 bean;

    @BeforeEach
    void setupMocks() {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("??");
    }

    @Test
    void fieldMocked(Graph graph) {
        assertEquals("??", mock.get());
        assertEquals(2, graph.draw().size());
    }

    @Test
    void fieldMockedAndInBeanDependency(Graph graph) {
        assertEquals("??", mock.get());
        assertEquals("??3", bean.get());
        assertEquals(2, graph.draw().size());
    }
}

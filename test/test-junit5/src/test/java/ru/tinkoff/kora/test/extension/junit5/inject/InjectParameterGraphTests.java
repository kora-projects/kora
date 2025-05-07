package ru.tinkoff.kora.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(
    value = TestApplication.class,
    components = TestComponent1.class)
public class InjectParameterGraphTests {

    @Test
    void injectOne(KoraAppGraph graph) {
        assertNotNull(graph);
    }
}

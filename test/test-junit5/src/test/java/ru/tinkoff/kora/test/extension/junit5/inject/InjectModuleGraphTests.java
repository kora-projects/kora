package ru.tinkoff.kora.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(value = TestApplication.class, modules = TestModule.class)
public class InjectModuleGraphTests {

    @Test
    void injectOne(KoraAppGraph graph) {
        assertNotNull(graph.getFirst(TestModule.SomeModuleService.class));
    }
}

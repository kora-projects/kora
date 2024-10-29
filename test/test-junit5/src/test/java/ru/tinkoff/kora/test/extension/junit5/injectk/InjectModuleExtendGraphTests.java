package ru.tinkoff.kora.test.extension.junit5.injectk;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestExtendModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(value = TestApplication.class, modules = TestExtendModule.class)
public class InjectModuleExtendGraphTests {

    @Test
    void injectOne(KoraAppGraph graph) {
        assertNotNull(graph.getFirst(TestExtendModule.SomeExtendModuleService.class));
    }
}

package io.koraframework.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestExtendModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(value = TestApplication.class, modules = TestExtendModule.class)
public class InjectModuleExtendGraphTests {

    @Test
    void injectOne(KoraAppGraph graph) {
        assertNotNull(graph.getFirst(TestExtendModule.SomeExtendModuleService.class));
    }
}

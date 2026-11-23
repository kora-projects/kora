package io.koraframework.test.extension.junit5.conditional;

import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.ConditionalComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
class ConditionFailedNodeTests {

    @Test
    void injectSkipsConditionFailedNodes(@TestComponent ConditionalComponent component) {
        assertEquals(new ConditionalComponent("enabled"), component);
    }

    @Test
    void getFirstSkipsConditionFailedNodes(KoraAppGraph graph) {
        assertEquals(new ConditionalComponent("enabled"), graph.getFirst(ConditionalComponent.class));
    }
}

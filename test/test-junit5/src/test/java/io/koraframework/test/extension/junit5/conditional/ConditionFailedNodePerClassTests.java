package io.koraframework.test.extension.junit5.conditional;

import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.ConditionalComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@KoraAppTest(TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConditionFailedNodePerClassTests {

    @TestComponent
    KoraAppGraph graph;

    @Test
    @Order(1)
    void getAllSkipsConditionFailedNodes() {
        assertThat(graph.getAll(ConditionalComponent.class))
            .containsExactly(new ConditionalComponent("enabled"));
    }

    @Test
    @Order(2)
    void secondTestResetsMocksOverConditionFailedNodes() {
        assertThat(graph.getAll(ConditionalComponent.class)).hasSize(1);
    }
}

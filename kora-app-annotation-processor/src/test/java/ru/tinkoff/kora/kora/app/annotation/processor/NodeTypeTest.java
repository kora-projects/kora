package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeTypeTest extends AbstractKoraAppTest {
    @Test
    public void testNodeType() {
        var draw = compile("""
            import java.util.HashMap;
            import java.util.Map;
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}

                default Map<String, TestClass1> testMap() { return new HashMap<>(); }
                @Root
                default TestClass1 root(Map<String, TestClass1> p0) { return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        var root = draw.init().block().get(draw.getNodes().get(1));
        var nodeType = (ParameterizedType) draw.getNodes().get(0).type();
        assertThat(nodeType.getRawType()).isEqualTo(Map.class);
        assertThat(nodeType.getActualTypeArguments()).containsExactly(String.class, root.getClass());
    }
}

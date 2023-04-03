package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConflictResolutionTest extends AbstractKoraAppTest {
    @Test
    public void testMultipleComponentCandidates() {
        var result = compile(List.of(new KoraAppProcessor()), """
            public interface TestInterface {}
            """, """
            public class TestImpl1 implements TestInterface {}
            """, """
            public class TestImpl2 implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root(TestInterface t) {return "";}
                default TestImpl1 testImpl1() { return new TestImpl1(); }
                default TestImpl2 testImpl2() { return new TestImpl2(); }
            }
            """);
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    public void testDefaultComponentOverride() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            public class TestImpl1 implements TestInterface {}
            """, """
            public class TestImpl2 implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root(TestInterface t) {return "";}
                @DefaultComponent
                default TestImpl1 testImpl1() { return new TestImpl1(); }
                default TestImpl2 testImpl2() { return new TestImpl2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        var g = draw.init().block();
        assertThat(g.get(draw.getNodes().get(0))).isInstanceOf(this.compileResult.loadClass("TestImpl2"));
    }

}

package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.GraphCondition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ConditionalComponentTest extends AbstractKoraAppTest {
    public static class MatchesCondition implements GraphCondition {
        @Override
        public ConditionResult eval() {
            return new ConditionResult.Matched("test");
        }
    }

    public static class FailedCondition implements GraphCondition {
        @Override
        public ConditionResult eval() {
            return new ConditionResult.Failed("test");
        }
    }

    @Test
    public void testConditionalOnClass() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface object) { return java.util.Objects.requireNonNull(object); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default GraphCondition matches() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition(); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
            }
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
            public class TestClass1 implements TestInterface{
            }
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
            public class TestClass2 implements TestInterface{
            }
            """, """
            public interface TestInterface {}
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

}

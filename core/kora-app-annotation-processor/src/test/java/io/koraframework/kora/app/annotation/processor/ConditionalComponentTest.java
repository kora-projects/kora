package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.GraphCondition;
import org.assertj.core.api.Assertions;
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
        assertThat(draw.getNodes()).hasSize(5);
        var graph = draw.init();
        var class1Node = draw.getNodes()
            .stream()
            .filter(n -> n.type().toString().contains("TestClass1"))
            .findFirst()
            .get();
        var class2Node = draw.getNodes()
            .stream()
            .filter(n -> n.type().toString().contains("TestClass2"))
            .findFirst()
            .get();
        Assertions.assertThat(graph.get(class1Node)).isNotNull();
        Assertions.assertThatThrownBy(() -> graph.get(class2Node))
            .hasMessage("Graph node value was not initialized because condition failed: test");
    }

    @Test
    public void testConditionFailedOnRoot() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default Object root(TestInterface object) { return java.util.Objects.requireNonNull(object); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
            }
            """, """
            @Component
            public class TestClass1 implements TestInterface{
            }
            """, """
            public interface TestInterface {}
            """);
        assertThat(draw.getNodes()).hasSize(3);
        var graph = draw.init();
        var conditionNode = draw.getNodes()
            .stream()
            .filter(n -> n.type().equals(GraphCondition.class))
            .findFirst()
            .get();
        for (var node : draw.getNodes()) {
            if (node == conditionNode) {
                Assertions.assertThat(graph.get(node)).isNotNull();
            } else {
                Assertions.assertThatThrownBy(() -> graph.get(node))
                    .hasMessage("Graph node value was not initialized because condition failed: test");
            }
        }
    }

    @Test
    public void testConditionFailedOnRootWithIntermediateNode() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default Object root(TestClass1 object) { return java.util.Objects.requireNonNull(object); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
            }
            """, """
            @Component
            public class TestClass1 {  public TestClass1(TestClass2 val){}  }
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
            public class TestClass2 {}
            """);
        assertThat(draw.getNodes()).hasSize(4);
        var graph = draw.init();
        var conditionNode = draw.getNodes()
            .stream()
            .filter(n -> n.type().equals(GraphCondition.class))
            .findFirst()
            .get();
        for (var node : draw.getNodes()) {
            if (node == conditionNode) {
                Assertions.assertThat(graph.get(node)).isNotNull();
            } else {
                Assertions.assertThatThrownBy(() -> graph.get(node));
            }
        }
    }

    @Test
    public void testMultipleRootConditions() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default Integer root1(TestClass1 object) { return 42; }
            
                @Root
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default String root2(TestClass1 object) { return java.util.Objects.requireNonNull(object).toString(); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default GraphCondition matches() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition(); }
            
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new  io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
            }
            """, """
            @Component
            public class TestClass1 {  public TestClass1(TestClass2 val){}  }
            """, """
            @Component
            public class TestClass2 {}
            """);
        assertThat(draw.getNodes()).hasSize(6);
        var graph = draw.init();
        for (var node : draw.getNodes()) {
            if (node.type().equals(Integer.class)) {
                Assertions.assertThatThrownBy(() -> graph.get(node));
            } else {
                Assertions.assertThat(graph.get(node)).isNotNull();
            }
        }
    }

    @Test
    public void testConditionalWithAll() {
        var draw = compile("""
            import io.koraframework.application.graph.All;import io.koraframework.application.graph.PromiseOf;import io.koraframework.application.graph.ValueOf;@KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(All<TestInterface> o1, All<PromiseOf<TestInterface>> o2, All<ValueOf<TestInterface>> o3) {
                    o1.forEach(java.util.Objects::requireNonNull);
                    o2.forEach(java.util.Objects::requireNonNull);
                    o3.forEach(java.util.Objects::requireNonNull);
                    return o2.iterator().next();
                }
            
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
        assertThat(draw.getNodes()).hasSize(5);
        var graph = draw.init();
        var class1Node = draw.getNodes()
            .stream()
            .filter(n -> n.type().toString().contains("TestClass1"))
            .findFirst()
            .get();
        var class2Node = draw.getNodes()
            .stream()
            .filter(n -> n.type().toString().contains("TestClass2"))
            .findFirst()
            .get();
        Assertions.assertThat(graph.get(class1Node)).isNotNull();
        Assertions.assertThatThrownBy(() -> graph.get(class2Node))
            .hasMessage("Graph node value was not initialized because condition failed: test");
    }

    @Test
    public void testConditionsWithDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass1 o1, TestClass2 o2) { return o1; }

                default Dep1 dep1() { return new Dep1(); }

                default Dep2 dep2() { return new Dep2(); }

                @Tag(Cond1.class)
                default GraphCondition cond1(Dep1 dep) { return new Cond1(); }

                @Tag(Cond2.class)
                default GraphCondition cond2(Dep2 dep) { return new Cond2(); }
            }
            """, """
            public class Dep1 {}
            """, """
            public class Dep2 {}
            """, """
            public class Cond1 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond1"); }
            }
            """, """
            public class Cond2 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond2"); }
            }
            """, """
            @Component
            @Conditional(tag = Cond1.class)
            public class TestClass1 {}
            """, """
            @Component
            @Conditional(tag = Cond2.class)
            public class TestClass2 {}
            """);

        var graph = draw.init();
        var class1Node = draw.getNodes().stream().filter(n -> n.type().toString().contains("TestClass1")).findFirst().get();
        var class2Node = draw.getNodes().stream().filter(n -> n.type().toString().contains("TestClass2")).findFirst().get();
        Assertions.assertThat(graph.get(class1Node)).isNotNull();
        Assertions.assertThat(graph.get(class2Node)).isNotNull();
    }

    @Test
    public void testConditionWithSeveralDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass1 o) { return o; }

                default Dep1 dep1() { return new Dep1(); }

                default Dep2 dep2(Dep1 dep) { return new Dep2(); }

                default Dep3 dep3(Dep2 dep) { return new Dep3(); }

                @Tag(Cond1.class)
                default GraphCondition cond1(Dep1 first, Dep3 last) { return new Cond1(); }
            }
            """, """
            public class Dep1 {}
            """, """
            public class Dep2 {}
            """, """
            public class Dep3 {}
            """, """
            public class Cond1 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond1"); }
            }
            """, """
            @Component
            @Conditional(tag = Cond1.class)
            public class TestClass1 {}
            """);

        var graph = draw.init();
        var class1Node = draw.getNodes().stream().filter(n -> n.type().toString().contains("TestClass1")).findFirst().get();
        Assertions.assertThat(graph.get(class1Node)).isNotNull();
    }
}

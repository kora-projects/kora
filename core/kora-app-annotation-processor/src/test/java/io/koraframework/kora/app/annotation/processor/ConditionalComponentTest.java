package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.GraphCondition;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ConditionalComponentTest extends AbstractKoraAppTest {
    private static final String FAILED = "io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition";

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
    public void testNullableDependencyOnConditionalComponent() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root(@Nullable TestClass1 matched, @Nullable TestClass2 failed) {
                    return (matched == null ? "null" : "matched") + "," + (failed == null ? "null" : "failed");
                }

                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default GraphCondition matches() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition(); }

                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
            }
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
            public class TestClass1 {}
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
            public class TestClass2 {}
            """);
        var graph = draw.init();
        var rootNode = draw.getNodes()
            .stream()
            .filter(n -> n.type().equals(String.class))
            .findFirst()
            .get();
        Assertions.assertThat(graph.get(rootNode)).isEqualTo("matched,null");
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

    @Test
    public void testCycleThroughAllIsReportedAsCircularDependency() {
        Assertions.assertThatThrownBy(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface o) { return o; }

                @Tag(Cond1.class)
                default GraphCondition cond1(All<TestInterface> all) { return new Cond1(); }
            }
            """, """
            public interface TestInterface {}
            """, """
            public class Cond1 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond1"); }
            }
            """, """
            @Component
            @Conditional(tag = Cond1.class)
            public class TestClass1 implements TestInterface {}
            """))
            .hasMessageContaining("Circular dependency found:");
    }

    @Test
    public void testPromisedProxyTargetIsCreatedWhenReachedOnlyThroughProxyOfUnconditionalRoot() throws Exception {
        promisedProxyTargetIsCreatedWhenReachedOnlyThroughProxyOfUnconditionalRoot("""
            @Root
            default U u(A a) { return new U(a); }
            @Root
            @Conditional(tag = FAILED.class)
            default R r(IB b) { return new R(b); }
            """);
    }

    @Test
    public void testPromisedProxyTargetIsCreatedWhenReachedOnlyThroughProxyOfUnconditionalRootDeclaredLast() throws Exception {
        promisedProxyTargetIsCreatedWhenReachedOnlyThroughProxyOfUnconditionalRoot("""
            @Root
            @Conditional(tag = FAILED.class)
            default R r(IB b) { return new R(b); }
            @Root
            default U u(A a) { return new U(a); }
            """);
    }

    private void promisedProxyTargetIsCreatedWhenReachedOnlyThroughProxyOfUnconditionalRoot(String roots) throws Exception {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(FAILED.class)
                default GraphCondition failed() { return new FAILED(); }

                interface IB { String b(); }
                final class A { public final IB b; public A(IB b) { this.b = b; } }
                final class B implements IB { public B(A a) {} public String b() { return "b"; } }
                final class U { public final A a; public U(A a) { this.a = a; } }
                final class R { public R(IB b) {} }

                default A a(IB b) { return new A(b); }
                default IB b(A a) { return new B(a); }
            %s
            }
            """.formatted(roots.indent(4)).replace("FAILED", FAILED));
        var graph = draw.init();
        var u = graph.get(draw.getNodes().stream().filter(n -> n.type().getTypeName().endsWith("$U")).findFirst().get());
        var a = u.getClass().getField("a").get(u);
        var b = a.getClass().getField("b").get(a);
        var ib = java.util.Arrays.stream(b.getClass().getInterfaces()).filter(i -> i.getSimpleName().equals("IB")).findFirst().orElseThrow();
        assertThat(ib.getMethod("b").invoke(b)).isEqualTo("b");
    }

    @Test
    public void testNullableAndOptionalValueOfAndPromiseOfConditionalComponentWithFailedCondition() throws Exception {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(%s.class)
                default GraphCondition failed() { return new %s(); }

                final class X {}

                @Conditional(tag = %s.class)
                default X x() { return new X(); }

                @Root
                default String root(Optional<ValueOf<X>> ov, Optional<PromiseOf<X>> op, @Nullable ValueOf<X> nv, @Nullable PromiseOf<X> np) {
                    return ov.isPresent() + "," + op.isPresent() + "," + (nv != null) + "," + (np != null);
                }
            }
            """.formatted(FAILED, FAILED, FAILED));
        var graph = draw.init();
        var rootNode = draw.getNodes().stream().filter(n -> n.type().equals(String.class)).findFirst().get();
        Assertions.assertThat(graph.get(rootNode)).isEqualTo("false,false,false,false");
    }
}

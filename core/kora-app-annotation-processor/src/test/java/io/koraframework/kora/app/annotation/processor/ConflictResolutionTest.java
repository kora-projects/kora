package io.koraframework.kora.app.annotation.processor;

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
        var g = draw.init();
        assertThat(g.get(draw.getNodes().get(0))).isInstanceOf(this.compileResult.loadClass("TestImpl2"));
    }

    @Test
    public void testDefaultComponentTemplateOverride() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface <T> {}
            """, """
            public class TestImpl1 <T> implements TestInterface<T> {}
            """, """
            public class TestImpl2 <T> implements TestInterface<T> {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root(TestInterface<String> t) {return "";}

                @DefaultComponent
                default <T> TestImpl1<T> testImpl1() { return new TestImpl1<>(); }
                default <T> TestImpl2<T> testImpl2() { return new TestImpl2<>(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        var g = draw.init();
        assertThat(g.get(draw.getNodes().get(0))).isInstanceOf(this.compileResult.loadClass("TestImpl2"));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentUsedWhenNoOverride() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            @Component
            @DefaultComponent
            public class DefaultTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface t) {return t;}
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(defaultImpl));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentOverrideByComponentClass() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            @Component
            @DefaultComponent
            public class DefaultTestImpl implements TestInterface {}
            """, """
            @Component
            public class OverrideTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface t) {return t;}
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var overrideImpl = this.compileResult.loadClass("OverrideTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(overrideImpl))
            .noneSatisfy(value -> assertThat(value).isInstanceOf(defaultImpl));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentOverrideByFactoryMethod() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            @Component
            @DefaultComponent
            public class DefaultTestImpl implements TestInterface {}
            """, """
            public class OverrideTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface t) {return t;}

                default OverrideTestImpl testImpl() { return new OverrideTestImpl(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var overrideImpl = this.compileResult.loadClass("OverrideTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(overrideImpl))
            .noneSatisfy(value -> assertThat(value).isInstanceOf(defaultImpl));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentOverrideBySameTagOnly() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            public final class DefaultTag {}
            """, """
            public final class OverrideTag {}
            """, """
            @Component
            @DefaultComponent
            @Tag(DefaultTag.class)
            public class DefaultTestImpl implements TestInterface {}
            """, """
            @Component
            @Tag(OverrideTag.class)
            public class OverrideTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(DefaultTag.class) TestInterface t) {return t;}
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var overrideImpl = this.compileResult.loadClass("OverrideTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(defaultImpl))
            .noneSatisfy(value -> assertThat(value).isInstanceOf(overrideImpl));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentOverrideByComponentClassWithSameTag() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            public final class TestTag {}
            """, """
            @Component
            @DefaultComponent
            @Tag(TestTag.class)
            public class DefaultTestImpl implements TestInterface {}
            """, """
            @Component
            @Tag(TestTag.class)
            public class OverrideTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(TestTag.class) TestInterface t) {return t;}
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var overrideImpl = this.compileResult.loadClass("OverrideTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(overrideImpl))
            .noneSatisfy(value -> assertThat(value).isInstanceOf(defaultImpl));
    }

    @Test
    public void testDefaultComponentAnnotatedComponentOverrideByConditionalComponent() throws ClassNotFoundException {
        var draw = compile("""
            public interface TestInterface {}
            """, """
            @Component
            @DefaultComponent
            public class DefaultTestImpl implements TestInterface {}
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
            public class OverrideTestImpl implements TestInterface {}
            """, """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface t) {return t;}

                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default GraphCondition matches() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);

        var defaultImpl = this.compileResult.loadClass("DefaultTestImpl");
        var overrideImpl = this.compileResult.loadClass("OverrideTestImpl");
        var g = draw.init();
        assertThat(draw.getNodes())
            .extracting(g::get)
            .anySatisfy(value -> assertThat(value).isInstanceOf(overrideImpl))
            .noneSatisfy(value -> assertThat(value).isInstanceOf(defaultImpl));
    }

}

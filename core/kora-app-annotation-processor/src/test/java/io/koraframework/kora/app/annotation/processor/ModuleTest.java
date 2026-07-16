package io.koraframework.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleTest extends AbstractKoraAppTest {

    @Test
    public void testAnnotatedModuleProvidesDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            @Module
            public interface TestModule {
                default TestClass testClass() { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testMixedInModuleProvidesDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication extends TestMixin {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public interface TestMixin {
                default TestClass testClass() { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testMultipleAnnotatedModules() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass1 c1, TestClass2 c2) { return c1; }
            }
            """, """
            public class TestClass1 {}
            """, """
            public class TestClass2 {}
            """, """
            @Module
            public interface Module1 {
                default TestClass1 testClass1() { return new TestClass1(); }
            }
            """, """
            @Module
            public interface Module2 {
                default TestClass2 testClass2() { return new TestClass2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testModuleInheritsFromBaseInterface() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass1 c1, TestClass2 c2) { return c1; }
            }
            """, """
            public class TestClass1 {}
            """, """
            public class TestClass2 {}
            """, """
            public interface BaseModule {
                default TestClass1 testClass1() { return new TestClass1(); }
            }
            """, """
            @Module
            public interface ExtendedModule extends BaseModule {
                default TestClass2 testClass2() { return new TestClass2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testModuleOverridesBaseInterfaceMethod() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public interface BaseModule {
                default TestClass testClass() { throw new IllegalStateException("base should not be called"); }
            }
            """, """
            @Module
            public interface OverrideModule extends BaseModule {
                @Override
                default TestClass testClass() { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testNestedModuleInsideApp() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            
                class TestClass {}
            
                @Module
                interface TestModule {
                    default TestClass testClass() { return new TestClass(); }
                }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testModuleWithTaggedComponent() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(TestModule.class) TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            @Module
            public interface TestModule {
                @Tag(TestModule.class)
                default TestClass testClass() { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testClassModuleProvidesDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            @Module
            public class TestModule {
                public TestClass testClass() { return new TestClass(); }
            }
            """);
        // 3 nodes: root, TestClass (from module method), TestModule (module instance)
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testClassModuleWithConstructorDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public class Dep {}
            """, """
            @Module
            public class TestModule {
                private final Dep dep;
                public TestModule(Dep dep) { this.dep = dep; }
                public TestClass testClass() { return new TestClass(); }
            }
            """, """
            @Module
            public interface DepProvider {
                default Dep dep() { return new Dep(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(4);
        draw.init();
    }

    @Test
    public void testClassModuleExtendsBaseModule() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(A a, B b) { return a; }
            }
            """, """
            public class A {}
            """, """
            public class B {}
            """, """
            public class BaseModule {
                public B b() { return new B(); }
            }
            """, """
            @Module
            public class ConcreteModule extends BaseModule {
                public A a() { return new A(); }
            }
            """);
        // 4 nodes: root, A (from ConcreteModule.a()), B (inherited from BaseModule.b()), ConcreteModule instance
        assertThat(draw.getNodes()).hasSize(4);
        draw.init();
    }

    @Test
    public void testClassModuleOverridesInheritedMethod() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public class BaseModule {
                public TestClass testClass() { throw new IllegalStateException("base must not be called"); }
            }
            """, """
            @Module
            public class ConcreteModule extends BaseModule {
                @Override
                public TestClass testClass() { return new TestClass(); }
            }
            """);
        // 3 nodes: root, TestClass, ConcreteModule
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testMethodModuleProvidesDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public class InnerModule {
                public TestClass testClass() { return new TestClass(); }
            }
            """, """
            @Module
            public interface OuterModule {
                @FactoryModule
                default InnerModule inner() { return new InnerModule(); }
            }
            """);
        // 3 nodes: root, TestClass (from inner), InnerModule (from outer.inner())
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testMethodModuleWithDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass cls) { return cls; }
            }
            """, """
            public class TestClass {}
            """, """
            public class Config {}
            """, """
            public class InnerModule {
                private final Config config;
                public InnerModule(Config config) { this.config = config; }
                public TestClass testClass() { return new TestClass(); }
            }
            """, """
            @Module
            public interface OuterModule {
                @FactoryModule
                default InnerModule inner(Config config) { return new InnerModule(config); }
                default Config config() { return new Config(); }
            }
            """);
        // 4 nodes: root, TestClass, InnerModule, Config
        assertThat(draw.getNodes()).hasSize(4);
        draw.init();
    }


    @Test
    public void testAppExtendsMultipleMixedInInterfaces() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication extends TestMixin1, TestMixin2 {
                @Root
                default Object root(TestClass1 c1, TestClass2 c2) { return c1; }
            }
            """, """
            public class TestClass1 {}
            """, """
            public class TestClass2 {}
            """, """
            public interface TestMixin1 {
                default TestClass1 testClass1() { return new TestClass1(); }
            }
            """, """
            public interface TestMixin2 {
                default TestClass2 testClass2() { return new TestClass2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testFactoryModuleTagPropagation() {
        var draw = compile("""
            import io.koraframework.common.annotation.Tag;@KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(Tag1.class) TestClass t1, @Tag(Tag2.class) TestClass t2) { return ""; }
            
                @Tag(Tag1.class)
                @FactoryModule
                default TestModule testModule() { return new TestModule(); }
            
                @Tag(Tag2.class)
                @FactoryModule
                default TestModule testModule2() { return new TestModule(); }
            
                @Tag(Tag1.class)
                default TestDependency testDependency1() { return new TestDependency(); }
            
                @Tag(Tag2.class)
                default TestDependency testDependency2() { return new TestDependency(); }
            }
            """, """
            public class Tag1 {}
            """, """
            public class Tag2 {}
            """, """
            public class TestClass {}
            """, """
            public class TestDependency {}
            """, """
            public class TestModule {
                @Tag(Tag.Factory.class)
                public TestClass testClass(@Tag(Tag.Factory.class) TestDependency dep) { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(7);
        draw.init();
    }

}

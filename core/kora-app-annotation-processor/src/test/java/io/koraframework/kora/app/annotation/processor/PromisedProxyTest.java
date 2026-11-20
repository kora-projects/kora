package io.koraframework.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PromisedProxyTest extends AbstractKoraAppTest {

    @Test
    public void proxyDelegatesMethodsInheritedFromBaseType() throws Exception {
        compile("""
            @KoraApp
            public interface ExampleApplication {
                class Base {
                    public String base() { return "base"; }
                }
                class Class1 extends Base {
                    public String hello() { return "hello"; }
                }
                class Class2 {
                    public Class2(Class1 value) {}
                }

                @Root
                default Class1 class1(Class2 value) { return new Class1(); }

                default Class2 class2(Class1 value) { return new Class2(value); }
            }
            """).init();

        assertThat(proxyMethods("$ExampleApplication_Class1_PromisedProxy")).contains("base", "hello");
    }

    @Test
    public void proxyImplementsMethodsOfSuperInterfaces() throws Exception {
        compile("""
            @KoraApp
            public interface ExampleApplication {
                interface Interface1 {
                    String one();
                    default String withDefault() { return one(); }
                }
                interface Interface2 extends Interface1 {
                    String two();
                }
                class Impl implements Interface2 {
                    public String one() { return "1"; }
                    public String two() { return "2"; }
                }
                class Class2 {
                    public Class2(Interface2 value) {}
                }

                @Root
                default Interface2 interface2(Class2 value) { return new Impl(); }

                default Class2 class2(Interface2 value) { return new Class2(value); }
            }
            """).init();

        assertThat(proxyMethods("$ExampleApplication_Interface2_PromisedProxy")).contains("one", "two", "withDefault");
    }

    @Test
    public void proxyImplementsGenericSuperInterface() throws Exception {
        compile("""
            @KoraApp
            public interface ExampleApplication {
                interface Interface1<T> {
                    T get();
                }
                interface Interface2 extends Interface1<String> {}
                class Impl implements Interface2 {
                    public String get() { return "1"; }
                }
                class Class2 {
                    public Class2(Interface2 value) {}
                }

                @Root
                default Interface2 interface2(Class2 value) { return new Impl(); }

                default Class2 class2(Interface2 value) { return new Class2(value); }
            }
            """).init();

        assertThat(proxyMethods("$ExampleApplication_Interface2_PromisedProxy")).contains("get");
    }

    @Test
    public void proxyImplementsMethodDeclaredInSeveralSuperInterfacesOnce() throws Exception {
        compile("""
            @KoraApp
            public interface ExampleApplication {
                interface Interface1 {
                    String one();
                }
                interface Interface2 {
                    String one();
                }
                interface Interface3 extends Interface1, Interface2 {}
                class Impl implements Interface3 {
                    public String one() { return "1"; }
                }
                class Class2 {
                    public Class2(Interface3 value) {}
                }

                @Root
                default Interface3 interface3(Class2 value) { return new Impl(); }

                default Class2 class2(Interface3 value) { return new Class2(value); }
            }
            """).init();

        assertThat(proxyMethods("$ExampleApplication_Interface3_PromisedProxy")).containsOnlyOnce("one");
    }

    @Test
    public void finalMethodInheritedFromBaseTypeIsReported() {
        assertThat(catchThrowable(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                class Base {
                    public final String base() { return "base"; }
                }
                class Class1 extends Base {
                    public String hello() { return "hello"; }
                }
                class Class2 {
                    public Class2(Class1 value) {}
                }

                @Root
                default Class1 class1(Class2 value) { return new Class1(); }

                default Class2 class2(Class1 value) { return new Class2(value); }
            }
            """))).isNotNull();

        assertThat(compileResult.errors().getFirst().getMessage(Locale.US))
            .contains("Circular dependency found:")
            .contains("has final methods: base().");
    }

    @Test
    public void proxySkipsNonPublicMethodsInheritedFromOtherPackage() throws Exception {
        compile("""
            @KoraApp
            public interface ExampleApplication {
                @SuppressWarnings("deprecation")
                class Class1 extends java.util.Observable {
                    public String hello() { return "hello"; }
                    protected String own() { return "own"; }
                }
                class Class2 {
                    public Class2(Class1 value) {}
                }

                @Root
                default Class1 class1(Class2 value) { return new Class1(); }

                default Class2 class2(Class1 value) { return new Class2(value); }
            }
            """).init();

        assertThat(proxyMethods("$ExampleApplication_Class1_PromisedProxy"))
            .contains("hello", "own", "addObserver", "notifyObservers")
            .doesNotContain("setChanged", "clearChanged");
    }

    private String[] proxyMethods(String proxyClassName) {
        return Arrays.stream(loadClass(proxyClassName).getDeclaredMethods())
            .filter(m -> !m.isBridge())
            .map(Method::getName)
            .toArray(String[]::new);
    }
}

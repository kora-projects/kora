package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WrappedDependenciesTests extends AbstractKoraAppTest {

    @Test
    public void testWrappedDependencyWithGeneric1() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
            
                @Root
                default Class5 class5gen1(ClassGen1<Integer> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen1ValueOf(ValueOf<ClassGen1<String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen1PromiseOf(PromiseOf<ClassGen1<String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen1Wrapped(Wrapped<ClassGen1<String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen1ValueOfWrapped(ValueOf<Wrapped<ClassGen1<Integer>>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen1PromiseOfWrapped(PromiseOf<Wrapped<ClassGen1<Integer>>> class1) {
                    return new Class5();
                }
            
            //    @Root
            //    default Class5 class5gen1All(All<ClassGen1<String>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen1AllValueOf(All<ValueOf<ClassGen1<String>>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen1AllWrapped(All<Wrapped<ClassGen1<String>>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen1AllValueOfWrapped(All<ValueOf<Wrapped<ClassGen1<String>>>> class1) {
            //        return new Class5();
            //    }
            
                default <T> Wrapped<ClassGen1<T>> classGen1ArgGen1Wrapped(ArgGen1<T> argGen1) {
                    var c1 = new ClassGen1<T>();
                    return () -> c1;
                }
            
                default ArgGen1<String> argGen1() {
                    return new ArgGen1<>() {};
                }
            
                default ArgGen1<Integer> argGen1int() {
                    return new ArgGen1<>() {};
                }
            
                class ClassGen1<T> {}
            
                interface ArgGen1<T> {}
            
                class Class5 {}
            }
            """);

        assertThat(draw.getNodes()).hasSize(10);
        var materializedGraph = draw.init();
        assertThat(materializedGraph).isNotNull();
    }

    @Test
    public void testWrappedDependencyWithGeneric2() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
            
                @Root
                default Class5 class5gen2(ClassGen2<String, Integer> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen2ValueOf(ValueOf<ClassGen2<String, String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen2PromiseOf(PromiseOf<ClassGen2<String, String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen2Wrapped(Wrapped<ClassGen2<String, String>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen2ValueOfWrapped(ValueOf<Wrapped<ClassGen2<String, Integer>>> class1) {
                    return new Class5();
                }
            
                @Root
                default Class5 class5gen2PromiseOfWrapped(PromiseOf<Wrapped<ClassGen2<String, Integer>>> class1) {
                    return new Class5();
                }
            
            //    @Root
            //    default Class5 class5gen2All(All<ClassGen2<String, Integer>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen2AllValueOf(All<ValueOf<ClassGen2<String, String>>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen2AllWrapped(All<Wrapped<ClassGen2<String, String>>> class1) {
            //        return new Class5();
            //    }
            //
            //    @Root
            //    default Class5 class5gen2AllValueOfWrapped(All<ValueOf<Wrapped<ClassGen2<String, Integer>>>> class1) {
            //        return new Class5();
            //    }
            
                default <K, V> Wrapped<ClassGen2<K, V>> classGen2ArgGen2Wrapped(ArgGen2<K, V> argGen2) {
                    var c1 = new ClassGen2<K, V>();
                    return () -> c1;
                }
            
                default ArgGen2<String, String> argGen2() {
                    return new ArgGen2<>() {};
                }
            
                default ArgGen2<String, Integer> argGen2int() {
                    return new ArgGen2<>() {};
                }
            
                class ClassGen2<K, V> {}
            
                interface ArgGen2<K, V> {}
            
                class Class5 {}
            }
            """);

        assertThat(draw.getNodes()).hasSize(10);
        var materializedGraph = draw.init();
        assertThat(materializedGraph).isNotNull();
    }
}

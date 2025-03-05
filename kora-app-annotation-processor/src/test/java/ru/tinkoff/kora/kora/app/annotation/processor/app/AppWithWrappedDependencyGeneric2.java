package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithWrappedDependencyGeneric2 {

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

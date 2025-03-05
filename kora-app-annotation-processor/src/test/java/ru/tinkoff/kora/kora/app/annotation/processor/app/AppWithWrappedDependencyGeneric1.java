package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithWrappedDependencyGeneric1 {

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

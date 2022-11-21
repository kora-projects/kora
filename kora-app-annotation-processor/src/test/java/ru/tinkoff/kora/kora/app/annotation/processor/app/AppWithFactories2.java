package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

import java.util.List;

@KoraApp
public interface AppWithFactories2 {

    default Class1 class1(GenericClass<List<Class1>, String> dependency) {
        return new Class1();
    }

    default <T> GenericClassImpl<List<T>> factory2(TypeRef<T> typeRef) {
        return new GenericClassImpl<>();
    }

    class GenericClass<T, Q> {}

    class GenericClassImpl<T> extends GenericClass<T, String> {}

    class Class1 implements MockLifecycle {}
}

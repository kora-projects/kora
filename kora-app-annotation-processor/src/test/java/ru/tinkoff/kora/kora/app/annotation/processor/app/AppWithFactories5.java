package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

import java.util.List;

@KoraApp
public interface AppWithFactories5 {

    default int intComponent() {
        return 0;
    }

    default <T extends List<Class1>> TwoGenericClass<T, Class2> factory1(TypeRef<T> typeRef1, TypeRef<Class2> typeRef2, int dependency) {
        return new TwoGenericClass<>();
    }

    default <T, Q> TwoGenericClass<T, Q> factory2(TypeRef<T> typeRef1, TypeRef<Q> typeRef2, String nonExistDependency) {
        return new TwoGenericClass<>();
    }

    default Class1 class1(TwoGenericClass<List<Class1>, Class2> genericClass) {
        return new Class1();
    }

    class TwoGenericClass<T, Q> {}

    class Class1 implements MockLifecycle {}

    class Class2 implements MockLifecycle {}
}

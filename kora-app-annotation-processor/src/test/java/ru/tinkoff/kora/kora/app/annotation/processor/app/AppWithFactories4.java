package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

import java.util.ArrayList;
import java.util.List;

@KoraApp
public interface AppWithFactories4 {

    default int intComponent() {
        return 0;
    }

    default <T extends List<Class1>> TwoGenericClass<T, Class2> factory(TypeRef<T> typeRef1, TypeRef<Class2> typeRef2, int dependency) {
        return new TwoGenericClass<>();
    }

    default Class1 class1(TwoGenericClass<ArrayList<Class1>, Class2> genericClass) {
        return new Class1();
    }

    class TwoGenericClass<T, Q> {}

    class Class1 implements MockLifecycle {}

    class Class2 implements MockLifecycle {}
}

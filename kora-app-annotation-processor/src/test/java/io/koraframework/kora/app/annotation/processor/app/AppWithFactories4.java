package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

import java.util.ArrayList;
import java.util.List;

@KoraApp
public interface AppWithFactories4 {

    default Integer intComponent() {
        return 0;
    }

    default <T extends List<Class1>> TwoGenericClass<T, Class2> factory(TypeRef<T> typeRef1, TypeRef<Class2> typeRef2, Integer dependency) {
        return new TwoGenericClass<>();
    }

    @Root
    default Class1 class1(TwoGenericClass<ArrayList<Class1>, Class2> genericClass) {
        return new Class1();
    }

    class TwoGenericClass<T, Q> {}

    class Class1 {}

    class Class2 {}
}

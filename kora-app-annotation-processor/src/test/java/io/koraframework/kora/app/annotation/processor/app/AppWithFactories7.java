package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.KoraApp;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithFactories7 {

    default Integer intComponent() {
        return 0;
    }

    default <T> GenericClass<T> factory1(TypeRef<T> typeRef, Integer dependency) {
        throw new IllegalStateException();
    }

    @Tag(Class1.class)
    default <T> GenericClass<T> factory2(TypeRef<T> typeRef, Integer dependency) {
        return new GenericClass<>();
    }

    @Root
    default Class1 class1(@Tag(Class1.class) GenericClass<Class1> class1) {
        return new Class1();
    }

    class GenericClass<T> {}

    class Class1 {}
}

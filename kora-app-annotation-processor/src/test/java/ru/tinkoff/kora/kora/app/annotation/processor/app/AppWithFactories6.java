package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithFactories6 {

    default <T> GenericClass<T> factory1(GenericClass<T> genericClass) {
        return new GenericClass<>();
    }

    @Root
    default Class2 class2(GenericClass<Class1> class1) {
        return new Class2();
    }


    final class GenericClass<T> {}

    class Class1 {}

    class Class2 {}
}

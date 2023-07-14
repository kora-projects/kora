package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithComponents {
    @Root
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    @Root
    default Class2 class2(Interface1 class3) {
        return new Class2(class3);
    }

    @Root
    default Class3 class3() {
        return new Class3();
    }

    @Root
    default GenericClass<? extends Class3> genericExtends(Class3 class3) {
        return new GenericClass<>(class3);
    }

    @Root
    default GenericClass<? super Class3> genericSuper(Class3 class3) {
        return new GenericClass<>(class3);
    }


    record Class1(Class2 class2) {}

    record Class2(Interface1 class3) {}

    interface Interface1 {}

    class Class3 implements Interface1 {}

    record GenericClass<T>(T t) {}

}

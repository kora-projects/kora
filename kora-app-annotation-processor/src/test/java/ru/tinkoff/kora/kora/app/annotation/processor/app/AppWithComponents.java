package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithComponents {
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    default Class2 class2(Interface1 class3) {
        return new Class2(class3);
    }

    default Class3 class3() {
        return new Class3();
    }

    default GenericClass<? extends Class3> genericExtends(Class3 class3) {
        return new GenericClass<>(class3);
    }

    default GenericClass<? super Class3> genericSuper(Class3 class3) {
        return new GenericClass<>(class3);
    }


    record Class1(Class2 class2) implements MockLifecycle {}

    record Class2(Interface1 class3) implements MockLifecycle {}

    interface Interface1 {}

    class Class3 implements MockLifecycle, Interface1 {}

    record GenericClass<T>(T t) implements MockLifecycle {}

}

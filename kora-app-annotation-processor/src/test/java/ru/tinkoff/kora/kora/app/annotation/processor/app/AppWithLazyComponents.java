package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithLazyComponents {
    default Class0 class0() {
        return new Class0();
    }

    default Class1 class1(Class0 class0) {
        return new Class1();
    }

    @Root
    default Class2 class2(Class1 class1) {
        return new Class2();
    }

    default Class3 class3(Class2 class2) {
        throw new RuntimeException();
    }

    class Class0 {}

    class Class1 {}

    class Class2 {}

    class Class3 {}
}

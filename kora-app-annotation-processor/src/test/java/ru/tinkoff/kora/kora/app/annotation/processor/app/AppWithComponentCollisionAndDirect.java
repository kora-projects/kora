package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithComponentCollisionAndDirect {
    default Class1 c1() {
        return new Class1();
    }

    default Class1 c2() {
        return new Class1();
    }

    default Class1 c3() {
        return new Class1();
    }

    default Class2 class2(Class1 class1) {
        return new Class2(class1);
    }


    class Class1 {}

    record Class2(Class1 class1) implements MockLifecycle {}
}

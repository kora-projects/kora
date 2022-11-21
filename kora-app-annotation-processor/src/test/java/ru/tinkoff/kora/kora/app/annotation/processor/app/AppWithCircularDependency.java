package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithCircularDependency {
    default Class1 class1(Class2 value) {
        return new Class1();
    }

    default Class2 class2(Class3 value) {
        return new Class2();
    }

    default Class3 class3(Class1 value) {
        return new Class3();
    }


    record Class1() implements MockLifecycle {}

    record Class2() implements MockLifecycle {}

    record Class3() implements MockLifecycle {}

}




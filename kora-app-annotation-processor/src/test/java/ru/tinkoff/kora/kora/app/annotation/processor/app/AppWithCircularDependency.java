package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithCircularDependency {
    @Root
    default Class1 class1(Class2 value) {
        return new Class1();
    }

    @Root
    default Class2 class2(Class3 value) {
        return new Class2();
    }

    @Root
    default Class3 class3(Class1 value) {
        return new Class3();
    }


    record Class1() {}

    record Class2() {}

    record Class3() {}

}




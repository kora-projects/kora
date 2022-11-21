package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithUnresolvedDependency {
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    default Class2 class2(Class3 class3) {
        return new Class2(class3);
    }

    record Class1(Class2 class2) implements MockLifecycle {}

    record Class2(Class3 class3) implements MockLifecycle {}

    class Class3 implements MockLifecycle {}
}

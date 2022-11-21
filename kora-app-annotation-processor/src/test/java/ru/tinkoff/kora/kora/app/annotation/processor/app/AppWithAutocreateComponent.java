package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithAutocreateComponent {
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }


    record Class1(Class2 class2) implements MockLifecycle {}

    final class Class2 implements MockLifecycle {
        public Class2() {}
    }
}


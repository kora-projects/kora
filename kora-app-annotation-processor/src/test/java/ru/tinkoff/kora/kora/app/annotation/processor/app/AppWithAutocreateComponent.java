package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithAutocreateComponent {
    @Root
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }


    record Class1(Class2 class2) {}

    final class Class2 {
        public Class2() {}
    }
}


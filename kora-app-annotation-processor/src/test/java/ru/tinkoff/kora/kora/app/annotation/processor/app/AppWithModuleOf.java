package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithModuleOf {
    @Root
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    record Class1(Class2 class2) {}

    record Class2() {}

    record Class3() {}

    default Class3 class2() {
        return new Class3();
    }

    @ru.tinkoff.kora.common.Module
    interface Module {
        default Class2 class2() {
            return new Class2();
        }
    }

}

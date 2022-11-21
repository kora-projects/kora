package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithModuleOf {
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    record Class1(Class2 class2) implements MockLifecycle {}

    record Class2() implements MockLifecycle {}

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

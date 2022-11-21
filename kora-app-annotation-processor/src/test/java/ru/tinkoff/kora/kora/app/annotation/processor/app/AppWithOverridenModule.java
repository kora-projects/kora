package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithOverridenModule extends Module2 {
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    record Class1(Class2 class2) implements MockLifecycle {}

    record Class2() implements MockLifecycle {}


}

interface Module {
    default AppWithOverridenModule.Class2 class2() {
        throw new IllegalStateException();
    }
}


interface Module2 extends Module {
    @Override
    default AppWithOverridenModule.Class2 class2() {
        return new AppWithOverridenModule.Class2();
    }
}

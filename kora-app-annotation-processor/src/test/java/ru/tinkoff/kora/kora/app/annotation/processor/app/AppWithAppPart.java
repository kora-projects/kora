package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraSubmodule;
import ru.tinkoff.kora.common.Tag;

@KoraSubmodule
public interface AppWithAppPart {
    default Class1 class1(Class2 class2) {
        return new Class1();
    }

    @Tag(Class1.class)
    default Class1 class1Tag(Class2 class2) {
        return new Class1();
    }

    class Class1 implements MockLifecycle {}

    class Class2 implements MockLifecycle {}

    @Component
    class Class3 implements MockLifecycle {}

    @ru.tinkoff.kora.common.Module
    interface Module {
        default Class2 class2() {
            return new Class2();
        }
    }
}

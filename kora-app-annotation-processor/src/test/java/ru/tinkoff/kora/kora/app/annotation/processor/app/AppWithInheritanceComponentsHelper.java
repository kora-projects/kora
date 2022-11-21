package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;

public interface AppWithInheritanceComponentsHelper {
    record Class1(Class2 class2) implements MockLifecycle {}

    record Class2(Class3 class3) implements MockLifecycle {}

    class Class3 implements MockLifecycle {}

    interface AppWithInheritanceComponentsModule1 {
        default Class1 class1(Class2 class2) {
            return new Class1(class2);
        }
    }

    interface AppWithInheritanceComponentsModule2 {
        default Class2 class2(Class3 class3) {
            return new Class2(class3);
        }
    }

    interface AppWithInheritanceComponentsModule3 {
        default Class3 class3() {
            return new Class3();
        }
    }
}

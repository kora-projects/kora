package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.annotation.Root;

public interface AppWithInheritanceComponentsHelper {
    record Class1(Class2 class2) {}

    record Class2(Class3 class3) {}

    class Class3 {}

    interface AppWithInheritanceComponentsModule1 {
        @Root
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

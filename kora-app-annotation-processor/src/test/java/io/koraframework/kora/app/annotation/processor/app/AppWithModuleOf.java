package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

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

    @io.koraframework.common.Module
    interface Module {
        default Class2 class2() {
            return new Class2();
        }
    }

}

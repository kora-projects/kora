package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

/**
 * Overriding module method has no @Override (as for modules from compiled jars, where @Override is not retained),
 * and both the base module and the overriding module are plugged in.
 */
@KoraApp
public interface AppWithOverridenModuleWithoutAnnotation extends OverrideBaseModule, OverrideChildModule {
    @Root
    default Class1 class1(Class2 class2) {
        return new Class1(class2);
    }

    record Class1(Class2 class2) {}

    record Class2() {}
}

interface OverrideBaseModule {
    default AppWithOverridenModuleWithoutAnnotation.Class2 class2() {
        throw new IllegalStateException();
    }
}

interface OverrideChildModule extends OverrideBaseModule {
    default AppWithOverridenModuleWithoutAnnotation.Class2 class2() {
        return new AppWithOverridenModuleWithoutAnnotation.Class2();
    }
}

package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithVolatileComponents {
    default Class1 class1() {
        return new Class1();
    }

    default Class2 class2(ValueOf<Class1> class1, Class4 class4) {
        return new Class2();
    }

    default Class3 class3() {
        return new Class3();
    }

    default Class4 class4() {
        return new Class4();
    }

    class Class1 implements MockLifecycle {}

    class Class2 implements MockLifecycle {}

    class Class3 implements MockLifecycle {}

    class Class4 {}
}

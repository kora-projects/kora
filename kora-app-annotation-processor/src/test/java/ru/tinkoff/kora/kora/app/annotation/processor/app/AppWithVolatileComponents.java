package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithVolatileComponents {
    default Class1 class1() {
        return new Class1();
    }

    @Root
    default Class2 class2(ValueOf<Class1> class1, Class4 class4) {
        return new Class2();
    }

    default Class3 class3() {
        return new Class3();
    }

    default Class4 class4() {
        return new Class4();
    }

    class Class1 {}

    class Class2 {}

    class Class3 {}

    class Class4 {}
}

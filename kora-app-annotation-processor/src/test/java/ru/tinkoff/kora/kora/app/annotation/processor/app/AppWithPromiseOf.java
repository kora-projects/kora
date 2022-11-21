package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.common.KoraApp;

import java.util.Optional;

@KoraApp
public interface AppWithPromiseOf {
    default Class1 class1() {
        return new Class1();
    }

    default Class2 class2(PromiseOf<Class1> class1PromiseOf) {
        return new Class2(class1PromiseOf);
    }

    default Class3 class3() {
        return new Class3();
    }

    default Class4 class4(PromiseOf<Class3> class1PromiseOf, Optional<PromiseOf<String>> stringPromiseOf) {
        return new Class4(class1PromiseOf);
    }

    default MockLifecycle mock(Class2 class2, Class4 class4) {
        return new MockLifecycle() {};
    }

    record Class1() {}

    record Class2(PromiseOf<Class1> promiseOf) {}

    record Class3() implements MockLifecycle {}

    record Class4(PromiseOf<Class3> promiseOf) implements MockLifecycle {}

}

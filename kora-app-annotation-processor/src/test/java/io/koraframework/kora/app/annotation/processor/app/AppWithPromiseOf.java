package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.PromiseOf;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

import java.util.Optional;

@KoraApp
public interface AppWithPromiseOf {
    default Class1 class1() {
        return new Class1();
    }

    @Root
    default Class2 class2(PromiseOf<Class1> class1PromiseOf) {
        return new Class2(class1PromiseOf);
    }

    default Class3 class3() {
        return new Class3();
    }

    @Root
    default Class4 class4(PromiseOf<Class3> class1PromiseOf, Optional<PromiseOf<String>> stringPromiseOf) {
        return new Class4(class1PromiseOf);
    }

    @Root
    default Object mock(Class2 class2, Class4 class4) {
        return new Object();
    }

    record Class1() {}

    record Class2(PromiseOf<Class1> promiseOf) {}

    record Class3() {}

    record Class4(PromiseOf<Class3> promiseOf) {}

}

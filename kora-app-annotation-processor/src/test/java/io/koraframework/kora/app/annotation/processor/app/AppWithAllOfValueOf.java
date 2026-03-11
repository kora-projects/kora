package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

import java.util.Objects;

@KoraApp
public interface AppWithAllOfValueOf {
    @Root
    default Class1 class1(All<ValueOf<Class2>> cls) {
        for (var cl : cls) {
            Objects.requireNonNull(cl.get());
        }
        return new Class1();
    }

    default Class2 class2() {
        return new Class2();
    }

    class Class1 {}

    class Class2 {}

}

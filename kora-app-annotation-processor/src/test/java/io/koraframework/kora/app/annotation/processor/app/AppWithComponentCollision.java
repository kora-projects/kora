package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithComponentCollision {
    @Root
    default Class1 c1() {
        return new Class1();
    }

    @Root
    default Class1 c2() {
        return new Class1();
    }

    @Root
    default Class1 c3() {
        return new Class1();
    }


    class Class1 {}
}

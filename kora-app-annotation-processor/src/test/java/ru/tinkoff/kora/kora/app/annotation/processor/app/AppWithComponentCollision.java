package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithComponentCollision {
    default Class1 c1() {
        return new Class1();
    }

    default Class1 c2() {
        return new Class1();
    }

    default Class1 c3() {
        return new Class1();
    }


    class Class1 implements MockLifecycle {}
}

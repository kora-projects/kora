package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithNestedClasses {
    default Root1.Nested nested1() {
        return new Root1.Nested();
    }

    default Root2.Nested nested2() {
        return new Root2.Nested();
    }

    class Root1 {
        public static class Nested implements MockLifecycle {}
    }

    interface Root2 {
        class Nested implements MockLifecycle {}
    }
}

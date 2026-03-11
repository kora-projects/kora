package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithNestedClasses {
    @Root
    default Root1.Nested nested1() {
        return new Root1.Nested();
    }

    @Root
    default Root2.Nested nested2() {
        return new Root2.Nested();
    }

    class Root1 {
        public static class Nested {}
    }

    interface Root2 {
        class Nested {}
    }
}

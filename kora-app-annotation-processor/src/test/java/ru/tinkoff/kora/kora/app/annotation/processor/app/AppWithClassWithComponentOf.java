package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithClassWithComponentOf {
    @Root
    default Object object1(Class1 class1) {
        return new Object();
    }

    @Root
    default Object object2(ValueOf<Class3> class1) {
        return new Object();
    }

    @Component
    class Class1 implements Lifecycle {
        private final Class2 class2;

        public Class1(Class2 class2) {
            this.class2 = class2;
        }

        @Override
        public void init() {}

        @Override
        public void release() {}
    }

    @Component
    class Class2 {}

    @Component
    class Class3 {}
}

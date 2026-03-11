package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Component;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

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

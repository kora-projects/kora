package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;

import static org.mockito.Mockito.mock;

@KoraApp
public interface AppWithExactMatch {

    default MockLifecycle mock0Super1(Superclass1 superclass) {
        return mock(MockLifecycle.class);
    }

    default MockLifecycle mock1(Class1 superclass) {
        return mock(MockLifecycle.class);
    }

    default MockLifecycle mock0Super2(Superclass2 superclass) {
        return mock(MockLifecycle.class);
    }

    default MockLifecycle mock2(Class2 superclass) {
        return mock(MockLifecycle.class);
    }

    default Class1 class1() {
        return new Class1();
    }

    default Class2 class2() {
        return new Class2();
    }

    default Superclass1 superclass() {
        return new Superclass1();
    }

    default Wrapped<Superclass2> superclass2() {
        return Superclass2::new;
    }


    class Superclass1 {}

    class Class1 extends Superclass1 {}

    class Superclass2 {}

    class Class2 extends Superclass2 {}
}

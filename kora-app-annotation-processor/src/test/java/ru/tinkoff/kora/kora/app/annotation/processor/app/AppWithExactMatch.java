package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithExactMatch {

    @Root
    default Object mock0Super1(Superclass1 superclass) {
        return new Object();
    }

    @Root
    default Object mock1(Class1 superclass) {
        return new Object();
    }

    @Root
    default Object mock0Super2(Superclass2 superclass) {
        return new Object();
    }

    @Root
    default Object mock2(Class2 superclass) {
        return new Object();
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

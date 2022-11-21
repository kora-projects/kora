package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithWrappedDependency {
    default Class2 class2(Class1 class1) {
        return new Class2();
    }

    default Class3 class3(ValueOf<Class1> class1) {
        return new Class3();
    }

    default Class4 class4(All<ValueOf<Class1>> class1) {
        return new Class4();
    }

    default Class2 class2ValueWrapped(Wrapped<Class1> class1) {
        return new Class2();
    }

    default Class3 class3Wrapped(ValueOf<Wrapped<Class1>> class1) {
        return new Class3();
    }

    default Class4 class4Wrapped(All<ValueOf<Wrapped<Class1>>> class1) {
        return new Class4();
    }


    default Wrapped<Class1> class1() {
        var c1 = new Class1();
        return () -> c1;
    }

    class Class1 {}

    class Class2 implements MockLifecycle {}

    class Class3 implements MockLifecycle {}

    class Class4 implements MockLifecycle {}
}

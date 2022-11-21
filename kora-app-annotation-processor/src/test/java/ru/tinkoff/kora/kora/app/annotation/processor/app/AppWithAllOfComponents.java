package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;

@KoraApp
public interface AppWithAllOfComponents {
    default Class1 class1() {
        return new Class1();
    }

    default Class2 class2() {
        return new Class2();
    }

    default Class3 class3(Class4 class4) {
        return new Class3(class4);
    }

    default Class5 class5() {
        return new Class5();
    }

    @Tag(Superclass.class)
    default Class5 class5WithTag() {
        return new Class5();
    }

    default ClassWithAllOf classWithAllOf(All<Superclass> allOfSuperclass) {
        return new ClassWithAllOf(allOfSuperclass);
    }

    @Tag(Superclass.class)
    default ClassWithAllOf classWithAllOfWithTag(@Tag(Superclass.class) All<Superclass> allOfSuperclass) {
        return new ClassWithAllOf(allOfSuperclass);
    }

    default ClassWithAllValueOf classWithAllValueOf(All<ValueOf<Superclass>> allOfSuperclass) {
        return new ClassWithAllValueOf(allOfSuperclass);
    }

    default ClassWithInterfaces classWithinterfaces(All<SomeInterface> allSomeInterfaces) {
        return new ClassWithInterfaces(allSomeInterfaces);
    }

    default ClassWithInterfacesValueOf classWithInterfacesValueOf(All<ValueOf<SomeInterface>> allSomeInterfaces) {
        return new ClassWithInterfacesValueOf(allSomeInterfaces);
    }

    default ClassWithAllOfAnyTag classWithAllOfAnyTag(@Tag(Tag.Any.class) All<Class5> class5All) {
        return new ClassWithAllOfAnyTag(class5All);
    }

    class Superclass implements MockLifecycle {}

    class Class1 extends Superclass implements SomeInterface {}

    class Class2 extends Superclass implements SomeInterface {}

    class Class3 extends Superclass {
        private final Class4 class4;

        public Class3(Class4 class4) {
            this.class4 = class4;
        }
    }


    final class Class4 extends Superclass {}

    final class Class5 extends Superclass {}


    record ClassWithAllOf(All<Superclass> allOfSuperclass) implements MockLifecycle {}


    record ClassWithAllValueOf(All<ValueOf<Superclass>> allOfSuperclass) implements MockLifecycle {}

    interface SomeInterface {}

    record ClassWithInterfaces(All<SomeInterface> allSomeInterfaces) implements MockLifecycle {}

    record ClassWithInterfacesValueOf(All<ValueOf<SomeInterface>> allSomeInterfaces) implements MockLifecycle {}

    record ClassWithAllOfAnyTag(@Tag(Tag.Any.class) All<Class5> class5All) implements MockLifecycle {}


}

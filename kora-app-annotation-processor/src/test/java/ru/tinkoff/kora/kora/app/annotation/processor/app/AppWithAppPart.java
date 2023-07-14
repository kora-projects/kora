package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraSubmodule;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

@KoraSubmodule
public interface AppWithAppPart {
    @Root
    default Class1 class1(Class2 class2) {
        return new Class1();
    }

    @Tag(Class1.class)
    @Root
    default Class1 class1Tag(@Tag(Class1.class) Class2 class2) {
        return new Class1();
    }

    @Root
    class Class1 {}

    @Root
    class Class2 {}

    @Component
    @Root
    class Class3 {}

    @Component
    @Root
    class Class4<T extends Number> {}

    @ru.tinkoff.kora.common.Module
    interface Module {
        @Root
        default Class2 class2() {
            return new Class2();
        }

        @Tag(Class1.class)
        @Root
        default Class2 class2Tagged() {
            return new Class2();
        }

        @Tag(Class1.class)
        @Root
        default <T extends Number> Class4<T> class4() {
            return new Class4<>();
        }
    }
}

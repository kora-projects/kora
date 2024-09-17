package ru.tinkoff.kora.kora.app.annotation.processor.app;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraSubmodule;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

import java.math.BigDecimal;

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

    @Component
    @Root
    class Class5 {

        private final String s;

        public Class5(@Nullable String s) {
            this.s = s;
        }
    }

    @ru.tinkoff.kora.common.Module
    interface Module {
        @Root
        default Class2 class2() {
            return new Class2();
        }

        @Tag(Class1.class)
        @Root
        default String class3WithDep(Class2 class2) {
            return "class3-" + class2.getClass().getSimpleName();
        }

        @Tag(Class2.class)
        @Root
        default String class3WithDepNullable(@Nullable BigDecimal bigDecimal) {
            return "class3-" + (bigDecimal == null ? "0" : bigDecimal.toPlainString());
        }

        @Tag(Class3.class)
        @Root
        default String class3WithTaggedDepNullable(@Tag(BigDecimal.class) @Nullable BigDecimal bigDecimal) {
            return "class3-" + (bigDecimal == null ? "0" : bigDecimal.toPlainString());
        }

        @Tag(Class4.class)
        @Root
        default String class4WithTaggedDep(@Tag(Class1.class) Class2 class2) {
            return "class3-" + class2.getClass().getSimpleName();
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

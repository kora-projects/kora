package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;

@KoraApp
public interface AppWithTag {

    @Tag(Tag1.class)
    default Class1 class1Tag1(@Tag(Tag1.class) Class2 class2) {
        return new Class1(class2);
    }

    @Tag(Tag2.class)
    default Class1 class1Tag2(@Tag(Tag2.class) Class2 class2) {
        return new Class1(class2);
    }

    @Tag(Tag1.class)
    default Class2 class2Tag1() {
        return new Class2();
    }

    @Tag(Tag2.class)
    default Class2 class2Tag2() {
        return new Class2();
    }

    class Tag1 {}

    class Tag2 {}

    record Class1(Class2 class2) implements MockLifecycle {}

    class Class2 implements MockLifecycle {}
}

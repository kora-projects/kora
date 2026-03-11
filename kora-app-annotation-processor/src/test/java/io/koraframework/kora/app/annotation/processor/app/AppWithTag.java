package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithTag {

    @Tag(Tag1.class)
    @Root
    default Class1 class1Tag1(@Tag(Tag1.class) Class2 class2) {
        return new Class1(class2);
    }

    @Tag(Tag2.class)
    @Root
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

    record Class1(Class2 class2) {}

    class Class2 {}
}

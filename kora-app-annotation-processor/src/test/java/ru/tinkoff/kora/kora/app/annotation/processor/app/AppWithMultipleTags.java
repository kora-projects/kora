package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

import java.util.List;

@KoraApp
public interface AppWithMultipleTags {
    @Root
    default Class1 nonTagged() {
        return new Class1();
    }

    @Tag({Tag1.class, Tag2.class, Tag3.class})
    @Root
    default Class1 tag1tag2tag3() {
        return new Class1();
    }

    @Tag({Tag2.class, Tag3.class})
    @Root
    default Class1 tag2Tag3() {
        return new Class1();
    }

    @Tag(Tag4.class)
    @Root
    default Class1 tag4() {
        return new Class1();
    }

    @Root
    default Class2 nonTaggedClass2(Class1 class1) {
        return new Class2(class1);
    }

    @Tag({Tag1.class, Tag2.class, Tag3.class})
    @Root
    default Class2 tag1tag2Tag3(@Tag({Tag1.class, Tag2.class, Tag3.class}) Class1 class1) {
        return new Class2(class1);
    }

    @Tag(Tag4.class)
    @Root
    default Class2 tag4(@Tag(Tag4.class) Class1 class1) {
        return new Class2(class1);
    }

    @Root
    default Class3 nonTagged(All<Class1> allOf1) {
        return new Class3(allOf1);
    }

    @Tag(AppWithMultipleTags.class)
    @Root
    default Class3 anyTagged(@Tag(Tag.Any.class) All<Class1> allOf1) {
        return new Class3(allOf1);
    }

    @Tag(Tag1.class)
    @Root
    default Class3 tag1(@Tag(Tag1.class) All<Class1> allOf1) {
        return new Class3(allOf1);
    }

    @Tag({Tag2.class, Tag3.class})
    @Root
    default Class3 tag2tag3(@Tag({Tag2.class, Tag3.class}) All<Class1> allOf1) {
        return new Class3(allOf1);
    }

    @Tag(Tag4.class)
    @Root
    default Class3 tag4(@Tag(Tag4.class) All<Class1> allOf1) {
        return new Class3(allOf1);
    }

    class Tag1 {}

    class Tag2 {}

    class Tag3 {}

    class Tag4 {}


    class Class1 {}

    record Class2(Class1 class1) {}

    record Class3(List<Class1> class1s) {}
}

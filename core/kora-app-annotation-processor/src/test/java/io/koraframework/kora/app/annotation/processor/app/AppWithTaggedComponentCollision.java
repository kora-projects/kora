package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import org.jspecify.annotations.Nullable;

@KoraApp
public interface AppWithTaggedComponentCollision {
    @Tag(Class1.class)
    default Class1 c1() {
        return new Class1();
    }

    @Tag(Class1.class)
    default Class1 c2() {
        return new Class1();
    }

    @Root
    default Class2 class2(@Tag(Class1.class) Class1 class1, @Tag(Class1.class) @Nullable Class2 other) {
        return new Class2(class1);
    }


    class Class1 {}

    record Class2(Class1 class1) {}
}

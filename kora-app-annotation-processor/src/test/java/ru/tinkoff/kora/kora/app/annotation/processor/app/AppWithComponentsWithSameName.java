package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithComponentsWithSameName {

    @Root
    default Class2 class2(ru.tinkoff.kora.kora.app.annotation.processor.somepackage.Class1 class1) {
        return new Class2(class1);
    }

    @Root
    default Class3 class3(ru.tinkoff.kora.kora.app.annotation.processor.otherpackage.Class1 class1) {
        return new Class3(class1);
    }


    record Class2(ru.tinkoff.kora.kora.app.annotation.processor.somepackage.Class1 class1) {}

    record Class3(ru.tinkoff.kora.kora.app.annotation.processor.otherpackage.Class1 class1) {}

}

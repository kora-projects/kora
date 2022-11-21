package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithComponentsWithSameName {

    default Class2 class2(ru.tinkoff.kora.kora.app.annotation.processor.somepackage.Class1 class1){
        return new Class2(class1);
    }

    default Class3 class3(ru.tinkoff.kora.kora.app.annotation.processor.otherpackage.Class1 class1){
        return new Class3(class1);
    }


    record Class2(ru.tinkoff.kora.kora.app.annotation.processor.somepackage.Class1 class1) implements MockLifecycle {}

    record Class3(ru.tinkoff.kora.kora.app.annotation.processor.otherpackage.Class1 class1) implements MockLifecycle {}

}

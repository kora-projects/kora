package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithInheritanceComponents extends
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule1,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule2,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule3 {

}

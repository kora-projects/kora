package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;

@KoraApp
public interface AppWithInheritanceComponents extends
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule1,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule2,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule3 {

}

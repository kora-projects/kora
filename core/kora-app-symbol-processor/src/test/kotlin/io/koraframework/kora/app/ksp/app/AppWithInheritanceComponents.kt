package io.koraframework.kora.app.ksp.app

import io.koraframework.common.annotation.KoraApp

@KoraApp
interface AppWithInheritanceComponents : AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule1,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule2,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule3

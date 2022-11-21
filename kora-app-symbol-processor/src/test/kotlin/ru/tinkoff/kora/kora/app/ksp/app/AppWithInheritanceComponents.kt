package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithInheritanceComponents : AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule1,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule2,
    AppWithInheritanceComponentsHelper.AppWithInheritanceComponentsModule3

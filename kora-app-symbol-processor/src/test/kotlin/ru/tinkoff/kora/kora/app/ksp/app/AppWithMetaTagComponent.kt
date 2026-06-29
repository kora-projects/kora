package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.KoraSubmodule
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Root

@KoraSubmodule
interface AppWithMetaTagComponent {

    @Tag(MetaTag::class)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.FUNCTION,
    )
    @Retention(AnnotationRetention.RUNTIME)
    annotation class MetaTag

    interface MetaTaggedDependency

    @Module
    interface MetaTagModule {
        @Tag(MetaTag::class)
        fun taggedDependency(): MetaTaggedDependency = object : MetaTaggedDependency {}
    }

    @Component
    @Root
    class MetaTagConsumer(@MetaTag val dependency: MetaTaggedDependency)
}

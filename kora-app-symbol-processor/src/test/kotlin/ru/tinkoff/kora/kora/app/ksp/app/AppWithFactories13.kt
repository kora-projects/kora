package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithFactories13 {

    @Root
    fun target(cl: ConfigValueExtractor<TestEnum>): Any = Any()

    fun <T> extractor1(ref: TypeRef<T>): ConfigValueExtractor<T> where T : Enum<T>, T : MarkerOne {
        return object : ConfigValueExtractor<T> {
            override fun extract(value: Any?): T? = null
        }
    }

    fun <T> extractor2(ref: TypeRef<T>): ConfigValueExtractor<T> where T : Enum<T>, T : MarkerTwo {
        return object : ConfigValueExtractor<T> {
            override fun extract(value: Any?): T? = null
        }
    }

    enum class TestEnum : MarkerOne

    interface MarkerOne

    interface MarkerTwo

    interface ConfigValueExtractor<T> {

        fun extract(value: Any?): T?
    }
}

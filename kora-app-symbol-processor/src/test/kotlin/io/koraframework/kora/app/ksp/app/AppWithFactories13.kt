package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.TypeRef
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

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

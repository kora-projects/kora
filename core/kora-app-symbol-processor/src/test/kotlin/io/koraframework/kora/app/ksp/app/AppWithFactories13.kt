package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.TypeRef
import io.koraframework.common.annotation.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithFactories13 {

    @Root
    fun target(cl: ConfigValueMapper<TestEnum>): Any = Any()

    fun <T> extractor1(ref: TypeRef<T>): ConfigValueMapper<T> where T : Enum<T>, T : MarkerOne {
        return object : ConfigValueMapper<T> {
            override fun extract(value: Any?): T? = null
        }
    }

    fun <T> extractor2(ref: TypeRef<T>): ConfigValueMapper<T> where T : Enum<T>, T : MarkerTwo {
        return object : ConfigValueMapper<T> {
            override fun extract(value: Any?): T? = null
        }
    }

    enum class TestEnum : MarkerOne

    interface MarkerOne

    interface MarkerTwo

    interface ConfigValueMapper<T> {

        fun extract(value: Any?): T?
    }
}

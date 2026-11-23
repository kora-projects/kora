package io.koraframework.test.extension.junit5.kotlin.testdata

interface CovariantProducer<out T> {
    fun produce(): T
}

open class CovariantPayload(val value: String)

package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle

@KoraApp
interface AppWithGenericWithArrays {
    fun genericInt(): Generic<Int> {
        return object : Generic<Int> {
            override fun to(t: Int): Int {
                return t
            }
        }
    }

    fun genericListInt(): Generic<List<Int>> {
        return object : Generic<List<Int>> {
            override fun to(t: List<Int>): List<Int> {
                return t
            }
        }
    }

    fun genericListByteArr(): Generic<List<ByteArray>> {
        return object : Generic<List<ByteArray>> {
            override fun to(t: List<ByteArray>): List<ByteArray> {
                return t
            }
        }
    }

    fun genericListObjectArr(): Generic<List<Array<Any>>> {
        return object : Generic<List<Array<Any>>> {
            override fun to(t: List<Array<Any>>): List<Array<Any>> {
                return t
            }
        }
    }

    interface Generic<T> : MockLifecycle {
        fun to(t: T): T
    }
}

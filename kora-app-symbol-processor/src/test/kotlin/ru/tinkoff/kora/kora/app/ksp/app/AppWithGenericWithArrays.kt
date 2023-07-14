package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithGenericWithArrays {
    @Root
    fun genericInt(): Generic<Int> {
        return object : Generic<Int> {
            override fun to(t: Int): Int {
                return t
            }
        }
    }

    @Root
    fun genericListInt(): Generic<List<Int>> {
        return object : Generic<List<Int>> {
            override fun to(t: List<Int>): List<Int> {
                return t
            }
        }
    }

    @Root
    fun genericListByteArr(): Generic<List<ByteArray>> {
        return object : Generic<List<ByteArray>> {
            override fun to(t: List<ByteArray>): List<ByteArray> {
                return t
            }
        }
    }

    @Root
    fun genericListObjectArr(): Generic<List<Array<Any>>> {
        return object : Generic<List<Array<Any>>> {
            override fun to(t: List<Array<Any>>): List<Array<Any>> {
                return t
            }
        }
    }

    interface Generic<T> {
        fun to(t: T): T
    }
}

package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithCycleProxy {
    @Root
    fun someClass(w1: JsonWriter<Class1>, w2: JsonWriter<Class3>) = Any()

    fun writer1(w: JsonWriter<Class2>) = Writer1()
    fun writer2(w: JsonWriter<Class1>) = Writer2()
    fun writer3(w: JsonWriter<Class4>) = Writer3()
    fun writer4(w: JsonWriter<Class3>) = Writer4()


    interface JsonWriter<T> {
        fun write(test: T)
        fun writeWithReturn(test: T): String
    }

    class Class1
    class Class2
    class Class3
    class Class4

    class Writer1 : JsonWriter<Class1> {
        override fun write(test: Class1) = TODO("Not yet implemented")
        override fun writeWithReturn(test: Class1) = TODO("Not yet implemented")
    }

    class Writer2 : JsonWriter<Class2> {
        override fun write(test: Class2) = TODO("Not yet implemented")
        override fun writeWithReturn(test: Class2) = TODO("Not yet implemented")
    }

    class Writer3 : JsonWriter<Class3> {
        override fun write(test: Class3) = TODO("Not yet implemented")
        override fun writeWithReturn(test: Class3) = TODO("Not yet implemented")
    }

    class Writer4 : JsonWriter<Class4> {
        override fun write(test: Class4) = TODO("Not yet implemented")
        override fun writeWithReturn(test: Class4) = TODO("Not yet implemented")
    }
}

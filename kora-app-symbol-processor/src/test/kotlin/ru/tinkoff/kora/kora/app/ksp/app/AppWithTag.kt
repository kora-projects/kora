package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Root
import java.util.*

@KoraApp
interface AppWithTag {
    @Tag(Tag1::class)
    @Root
    fun class1Tag1(@Tag(Tag1::class) class2: Class2): Class1 {
        return Class1(class2)
    }

    @Tag(Tag2::class)
    @Root
    fun class1Tag2(@Tag(Tag2::class) class2: Class2): Class1 {
        return Class1(class2)
    }

    @Tag(Tag1::class)
    fun class2Tag1(): Class2 {
        return Class2()
    }

    @Tag(Tag2::class)
    fun class2Tag2(): Class2 {
        return Class2()
    }

    class Tag1
    class Tag2
    data class Class1(val class2: Class2)

    class Class2
}

package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithMultipleTags {
    fun nonTagged(): Class1 {
        return Class1()
    }

    @Tag(Tag1::class, Tag2::class, Tag3::class)
    fun tag1tag2tag3(): Class1 {
        return Class1()
    }

    @Tag(Tag2::class, Tag3::class)
    fun tag2Tag3(): Class1 {
        return Class1()
    }

    @Tag(Tag4::class)
    fun tag4(): Class1 {
        return Class1()
    }

    @Root
    fun nonTaggedClass2(class1: Class1): Class2 {
        return Class2(class1)
    }

    @Tag(Tag1::class, Tag2::class, Tag3::class)
    @Root
    fun tag1tag2Tag3(
        @Tag(
            Tag1::class, Tag2::class, Tag3::class
        ) class1: Class1
    ): Class2 {
        return Class2(class1)
    }

    @Tag(Tag4::class)
    @Root
    fun tag4(
        @Tag(
            Tag4::class
        ) class1: Class1
    ): Class2 {
        return Class2(class1)
    }

    @Root
    fun nonTagged(allOf1: All<Class1>): Class3 {
        return Class3(allOf1)
    }

    @Tag(AppWithMultipleTags::class)
    @Root
    fun anyTagged(
        @Tag(
            Tag.Any::class
        ) allOf1: All<Class1>
    ): Class3 {
        return Class3(allOf1)
    }

    @Tag(Tag1::class)
    @Root
    fun tag1(
        @Tag(
            Tag1::class
        ) allOf1: All<Class1>
    ): Class3 {
        return Class3(allOf1)
    }

    @Tag(Tag2::class, Tag3::class)
    @Root
    fun tag2tag3(
        @Tag(
            Tag2::class, Tag3::class
        ) allOf1: All<Class1>
    ): Class3 {
        return Class3(allOf1)
    }

    @Tag(Tag4::class)
    @Root
    fun tag4(
        @Tag(
            Tag4::class
        ) allOf1: All<Class1>
    ): Class3 {
        return Class3(allOf1)
    }

    class Tag1
    class Tag2
    class Tag3
    class Tag4
    class Class1
    data class Class2(val class1: Class1)

    data class Class3(val class1s: List<Class1>)
}

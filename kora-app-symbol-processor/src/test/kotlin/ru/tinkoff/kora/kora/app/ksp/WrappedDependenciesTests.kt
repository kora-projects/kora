package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WrappedDependenciesTests : AbstractKoraAppProcessorTest() {

    @Test
    fun testWrappedDependencyWithClass() {
        val draw = compile(
            """
            @KoraApp
            interface AppWithWrappedDependency {
                @Root
                fun class2(class1: Class1): Class2 {
                    return Class2()
                }

                @Root
                fun class3(class1: ValueOf<Class1>): Class3 {
                    return Class3()
                }

                @Root
                fun class4(class1: All<ValueOf<Class1>>): Class4 {
                    return Class4()
                }

                @Root
                fun class2ValueWrapped(class1: Wrapped<Class1>): Class2 {
                    return Class2()
                }

                @Root
                fun class3Wrapped(class1: ValueOf<Wrapped<Class1>>): Class3 {
                    return Class3()
                }

                @Root
                fun class4Wrapped(class1: All<ValueOf<Wrapped<Class1>>>): Class4 {
                    return Class4()
                }

                fun class1(): Wrapped<Class1> {
                    val c1 = Class1()
                    return Wrapped { c1 }
                }

                class Class1
                class Class2
                class Class3
                class Class4
            }
            """.trimIndent()
        )

        assertThat(draw.nodes).hasSize(7)
        val materializedGraph = draw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun testWrappedDependencyWithGeneric1() {
        val draw = compile(
            """
            @KoraApp
            interface AppWithWrappedDependencyGeneric1 {

                @Root
                fun class5gen1(class1: ClassGen1<Int>): Class5 = Class5()

                @Root
                fun class5gen1ValueOf(class1: ValueOf<ClassGen1<String>>): Class5 = Class5()

                @Root
                fun class5gen1PromiseOf(class1: PromiseOf<ClassGen1<String>>): Class5 = Class5()

                @Root
                fun class5gen1Wrapped(class1: Wrapped<ClassGen1<String>>): Class5 = Class5()

                @Root
                fun class5gen1ValueOfWrapped(class1: ValueOf<Wrapped<ClassGen1<Int>>>): Class5 = Class5()

                @Root
                fun class5gen1PromiseOfWrapped(class1: PromiseOf<Wrapped<ClassGen1<Int>>>): Class5 = Class5()

                @Root
                fun class5gen1All(class1: All<ClassGen1<String>>): Class5 = Class5()

                @Root
                fun class5gen1AllValueOf(class1: All<ValueOf<ClassGen1<String>>>): Class5 = Class5()

                @Root
                fun class5gen1AllWrapped(class1: All<Wrapped<ClassGen1<String>>>): Class5 = Class5()

                @Root
                fun class5gen1AllValueOfWrapped(class1: All<ValueOf<Wrapped<ClassGen1<String>>>>): Class5 = Class5()

                fun <T> classGen1ArgGen1Wrapped(argGen1: ArgGen1<T>): Wrapped<ClassGen1<T>> {
                    val c1 = ClassGen1<T>()
                    return Wrapped { c1 }
                }

                fun argGen1(): ArgGen1<String> = object : ArgGen1<String> {}

                fun argGen1int(): ArgGen1<Int> = object : ArgGen1<Int> {}

                class ClassGen1<T>

                interface ArgGen1<T>

                class Class5
            }
            """.trimIndent()
        )

        assertThat(draw.nodes).hasSize(16)
        val materializedGraph = draw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun testWrappedDependencyWithGeneric2() {
        val draw = compile(
            """
            @KoraApp
            interface AppWithWrappedDependencyGeneric2 {

                @Root
                fun class5gen2(class1: ClassGen2<String, Int>): Class5 = Class5()

                @Root
                fun class5gen2ValueOf(class1: ValueOf<ClassGen2<String, String>>): Class5 = Class5()

                @Root
                fun class5gen2PromiseOf(class1: PromiseOf<ClassGen2<String, String>>): Class5 = Class5()

                @Root
                fun class5gen2Wrapped(class1: Wrapped<ClassGen2<String, String>>): Class5 = Class5()

                @Root
                fun class5gen2ValueOfWrapped(class1: ValueOf<Wrapped<ClassGen2<String, Int>>>): Class5 = Class5()

                @Root
                fun class5gen2PromiseOfWrapped(class1: PromiseOf<Wrapped<ClassGen2<String, Int>>>): Class5 = Class5()

                @Root
                fun class5gen2All(class1: All<ClassGen2<String, String>>): Class5 = Class5()

                @Root
                fun class5gen2AllValueOf(class1: All<ValueOf<ClassGen2<String, String>>>): Class5 = Class5()

                @Root
                fun class5gen2AllWrapped(class1: All<Wrapped<ClassGen2<String, Int>>>): Class5 = Class5()

                @Root
                fun class5gen2AllValueOfWrapped(class1: All<ValueOf<Wrapped<ClassGen2<String, Int>>>>): Class5 = Class5()

                fun <K, V> classGen2ArgGen2Wrapped(argGen2: ArgGen2<K, V>): Wrapped<ClassGen2<K, V>> {
                    val c1 = ClassGen2<K, V>()
                    return Wrapped { c1 }
                }

                fun argGen2(): ArgGen2<String, String> = object : ArgGen2<String, String> {}

                fun argGen2int(): ArgGen2<String, Int> = object : ArgGen2<String, Int> {}

                class ClassGen2<K, V>

                interface ArgGen2<K, V>

                class Class5
            }
            """.trimIndent()
        )

        assertThat(draw.nodes).hasSize(16)
        val materializedGraph = draw.init()
        assertThat(materializedGraph).isNotNull
    }
}

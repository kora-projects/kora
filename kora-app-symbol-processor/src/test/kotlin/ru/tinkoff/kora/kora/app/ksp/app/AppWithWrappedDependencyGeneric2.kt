package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.PromiseOf
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.application.graph.Wrapped
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

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

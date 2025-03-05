package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.PromiseOf
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.application.graph.Wrapped
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

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

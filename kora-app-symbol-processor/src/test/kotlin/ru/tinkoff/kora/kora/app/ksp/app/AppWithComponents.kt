package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraSubmodule
import org.mockito.Mockito
import java.util.*

@KoraApp
interface AppWithComponents {
    fun class1(class2: Class2) = Class1(class2)

    fun class2(class3: Interface1) = Class2(class3)

    fun class3() = Class3()

    fun genericExtends(class3: Class3) = GenericClass(class3)

    fun genericSuper(class3: Class3) = GenericClass(class3)

    data class Class1(val class2: Class2) : MockLifecycle

    data class Class2(val class3: Interface1) : MockLifecycle

    interface Interface1
    class Class3 : MockLifecycle, Interface1
    data class GenericClass<T>(val t: T) : MockLifecycle
}

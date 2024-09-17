package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.KoraSubmodule
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Root
import java.math.BigDecimal

@KoraSubmodule
interface AppWithAppPart {

    @Root
    fun class1(class2: Class2): Class1 = Class1()

    @Tag(Class1::class)
    @Root
    fun class1Tag(class2: @Tag(Class1::class) Class2): Class1 = Class1()

    @Root
    class Class1

    @Root
    class Class2

    @Component
    @Root
    class Class3

    @Component
    @Root
    class Class4<T : Number?>

    @Component
    @Root
    class Class5(private val s: String?)

    @ru.tinkoff.kora.common.Module
    interface Module {

        @Root
        fun class2(): Class2 = Class2()

        @Tag(Class1::class)
        @Root
        fun class3WithDep(class2: Class2): String = "class3-" + class2.javaClass.simpleName

        @Tag(Class2::class)
        @Root
        fun class3WithDepNullable(bigDecimal: BigDecimal?): String = "class3-" + if (bigDecimal == null) "0" else bigDecimal.toPlainString()

        @Tag(Class3::class)
        @Root
        fun class3WithTaggedDepNullable(@Tag(BigDecimal::class) bigDecimal: BigDecimal?): String = "class3-" + if (bigDecimal == null) "0" else bigDecimal.toPlainString()

        @Tag(Class4::class)
        @Root
        fun class4WithTaggedDep(@Tag(Class1::class) class2: Class2): String = "class3-" + class2::class.simpleName!!

        @Tag(Class1::class)
        @Root
        fun class2Tagged(): Class2 = Class2()

        @Tag(Class1::class)
        @Root
        fun <T : Number> class4(): Class4<T> = Class4()
    }
}

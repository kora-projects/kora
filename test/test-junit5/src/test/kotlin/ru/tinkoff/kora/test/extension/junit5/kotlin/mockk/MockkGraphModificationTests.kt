package ru.tinkoff.kora.test.extension.junit5.kotlin.mockk

import io.mockk.every
import io.mockk.mockkClass
import org.jspecify.annotations.NullMarked
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.*
import java.util.function.Supplier

@KoraAppTest(TestApplication::class)
@NullMarked
class MockkGraphModificationTests : KoraAppTestGraphModifier {

    @TestComponent
    lateinit var mock1: TestComponent1

    @Tag(LifecycleComponent::class)
    @TestComponent
    lateinit var mock2: TestComponent2

    @TestComponent
    lateinit var component23: TestComponent23

    override fun graph(): KoraGraphModification {
        return KoraGraphModification.create()
            .replaceComponent(
                TestComponent1::class.java,
                Supplier { mockkClass(TestComponent1::class) })
            .replaceComponent(
                TestComponent2::class.java, LifecycleComponent::class.java,
                Supplier { mockkClass(TestComponent2::class) })
    }

    @Test
    fun mockFromGraph() {
        every { mock1.get() } returns "?"
        assertEquals("?", mock1.get())
    }

    @Test
    fun mockFromGraphWithTag() {
        every { mock2.get() } returns "?"
        assertEquals("?", mock2.get())
    }

    @Test
    fun mockBeanDependency() {
        every { mock2.get() } returns "?"
        assertEquals("?", mock2.get())
        assertEquals("?3", component23.get())
    }
}

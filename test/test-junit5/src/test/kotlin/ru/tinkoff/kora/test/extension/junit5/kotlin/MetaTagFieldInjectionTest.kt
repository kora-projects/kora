package ru.tinkoff.kora.test.extension.junit5.kotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.ComplexOtherMetaTag
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication

@KoraAppTest(TestApplication::class)
class MetaTagFieldInjectionTest {

    @ComplexOtherMetaTag
    @TestComponent
    lateinit var dep: String

    @Test
    fun metaTaggedFieldInjected() {
        assertEquals("other-1", dep)
    }
}

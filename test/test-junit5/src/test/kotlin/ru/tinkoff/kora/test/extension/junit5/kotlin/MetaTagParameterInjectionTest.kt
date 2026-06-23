package ru.tinkoff.kora.test.extension.junit5.kotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.TestComponent
import ru.tinkoff.kora.test.extension.junit5.testdata.ComplexOtherMetaTag
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication

@KoraAppTest(TestApplication::class)
class MetaTagConstructorInjectionTest(
    @ComplexOtherMetaTag @TestComponent val dep: String,
) {

    @Test
    fun metaTaggedConstructorParamInjected() {
        assertEquals("other-1", dep)
    }
}

@KoraAppTest(TestApplication::class)
class MetaTagMethodInjectionTest {

    @Test
    fun metaTaggedMethodParamInjected(@ComplexOtherMetaTag @TestComponent dep: String) {
        assertEquals("other-1", dep)
    }
}

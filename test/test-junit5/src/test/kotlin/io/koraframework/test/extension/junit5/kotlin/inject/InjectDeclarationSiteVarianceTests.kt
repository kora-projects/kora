package io.koraframework.test.extension.junit5.kotlin.inject

import io.koraframework.test.extension.junit5.KoraAppTest
import io.koraframework.test.extension.junit5.TestComponent
import io.koraframework.test.extension.junit5.kotlin.testdata.CovariantPayload
import io.koraframework.test.extension.junit5.kotlin.testdata.CovariantProducer
import io.koraframework.test.extension.junit5.testdata.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KoraAppTest(TestApplication::class)
class InjectDeclarationSiteVarianceTests {

    @TestComponent
    lateinit var field: CovariantProducer<CovariantPayload>

    @Test
    fun fieldInjected() {
        assertEquals("payload", field.produce().value)
    }

    @Test
    fun parameterInjected(@TestComponent parameter: CovariantProducer<CovariantPayload>) {
        assertEquals("payload", parameter.produce().value)
    }
}

package ru.tinkoff.kora.logging.symbol.processor.aop

import com.fasterxml.jackson.core.JsonGenerator
import org.assertj.core.api.Assertions
import org.assertj.core.api.InstanceOfAssertFactories
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.logging.common.arg.StructuredArgument
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter

abstract class AbstractLogAspectTest : AbstractSymbolProcessorTest() {
    protected var loggers = mutableMapOf<String, Logger>()
    protected var factory = Mockito.mock(ILoggerFactory::class.java)
    protected var inData = ArgumentCaptor.forClass(Marker::class.java)
    protected var outData = ArgumentCaptor.forClass(Marker::class.java)

    @BeforeEach
    open fun setUp() {
        Mockito.`when`(factory.getLogger(Mockito.any())).then { invocation: InvocationOnMock ->
            loggers.computeIfAbsent(invocation.getArgument(0, String::class.java)) {
                Mockito.mock(Logger::class.java)
            }
        }
    }

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.logging.common.annotation.Log
            import org.slf4j.event.Level.*
            import org.slf4j.Logger
            import org.slf4j.LoggerFactory
            
            """.trimIndent();
    }

    protected open fun compile(@Language("kotlin") vararg sources: String): TestObject {
        val compileResult = compile(listOf(AopSymbolProcessorProvider()), *sources)

        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val objectClass = compileResult.loadClass("\$Target__AopProxy")
        val objectInstance = objectClass.constructors[0].newInstance(factory)

        return TestObject(objectClass.kotlin, objectInstance)
    }


    protected open fun reset(log: Logger, level: Level?) {
        Mockito.reset(log)
        if (level == null) {
            return
        }

        if (level.ordinal >= Level.TRACE.ordinal) {
            Mockito.`when`(log.isTraceEnabled).thenReturn(true)
            Mockito.`when`(log.isEnabledForLevel(Level.TRACE)).thenReturn(true)
        }

        if (level.ordinal >= Level.DEBUG.ordinal) {
            Mockito.`when`(log.isDebugEnabled).thenReturn(true)
            Mockito.`when`(log.isEnabledForLevel(Level.DEBUG)).thenReturn(true)
        }

        if (level.ordinal >= Level.INFO.ordinal) {
            Mockito.`when`(log.isInfoEnabled).thenReturn(true)
            Mockito.`when`(log.isEnabledForLevel(Level.INFO)).thenReturn(true)
        }

        if (level.ordinal >= Level.WARN.ordinal) {
            Mockito.`when`(log.isWarnEnabled).thenReturn(true)
            Mockito.`when`(log.isEnabledForLevel(Level.WARN)).thenReturn(true)
        }

        if (level.ordinal >= Level.ERROR.ordinal) {
            Mockito.`when`(log.isErrorEnabled).thenReturn(true)
            Mockito.`when`(log.isEnabledForLevel(Level.ERROR)).thenReturn(true)
        }
    }

    protected open fun verifyInData(expectedData: Map<String, String>) {
        verifyData(inData, expectedData)
    }

    protected open fun verifyOutData(expectedData: Map<String, String>) {
        verifyData(outData, expectedData)
    }

    protected open fun verifyData(captor: ArgumentCaptor<Marker>, expectedData: Map<String, String>) {
        Assertions.assertThat(captor.value)
            .isNotNull
            .asInstanceOf(InstanceOfAssertFactories.type(StructuredArgument::class.java))
            .extracting { obj: StructuredArgument -> obj.fieldName() }
            .isEqualTo("data")
        val writer = captor.value as StructuredArgumentWriter
        val mockGen = Mockito.mock(JsonGenerator::class.java)
        val data = HashMap<String, String>()
        Mockito.doAnswer { invocation: InvocationOnMock ->
            data.put(
                invocation.getArgument(0, String::class.java),
                invocation.getArgument(1, String::class.java)
            )
        }.`when`(mockGen).writeStringField(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())
        writer.writeTo(mockGen)
        Assertions.assertThat(data).containsExactlyInAnyOrderEntriesOf(expectedData)
    }
}

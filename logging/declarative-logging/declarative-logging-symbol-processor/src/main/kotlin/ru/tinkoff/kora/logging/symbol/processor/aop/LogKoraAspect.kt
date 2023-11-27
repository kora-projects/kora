package ru.tinkoff.kora.logging.symbol.processor.aop

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import org.slf4j.event.Level
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.nextControlFlow
import ru.tinkoff.kora.ksp.common.parseMappingData

class LogKoraAspect : KoraAspect {

    private companion object {
        private const val RESULT_FIELD_NAME = "__result"
        private const val DATA_IN_FIELD_NAME = "__dataIn"
        private const val DATA_OUT_FIELD_NAME = "__dataOut"
        private const val DATA_PARAMETER_NAME = "data"
        private const val OUT_PARAMETER_NAME = "out"
        private const val MARKER_GENERATOR_PARAMETER_NAME = "gen"

        private const val MESSAGE_IN = ">"
        private const val MESSAGE_OUT = "<"

        val logAnnotation = ClassName("ru.tinkoff.kora.logging.common.annotation", "Log")
        val logInAnnotation = logAnnotation.nestedClass("in")
        val logOutAnnotation = logAnnotation.nestedClass("out")
        val logOffAnnotation = logAnnotation.nestedClass("off")
        val logResultAnnotation = logAnnotation.nestedClass("result")
        val structuredArgument = ClassName("ru.tinkoff.kora.logging.common.arg", "StructuredArgument")
        val structuredArgumentMapper = ClassName("ru.tinkoff.kora.logging.common.arg", "StructuredArgumentMapper")
        val iLoggerFactoryType = ClassName("org.slf4j", "ILoggerFactory")
        val loggerType = ClassName("org.slf4j", "Logger")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(logAnnotation.canonicalName, logInAnnotation.canonicalName, logOutAnnotation.canonicalName)
    }

    override fun apply(
        function: KSFunctionDeclaration,
        superCall: String,
        aspectContext: KoraAspect.AspectContext
    ): KoraAspect.ApplyResult {
        val loggerFactoryFieldName = aspectContext.fieldFactory.constructorParam(iLoggerFactoryType, emptyList())
        val declarationName = function.parentDeclaration?.qualifiedName?.asString()
        val loggerName = "${declarationName}.${function.simpleName.getShortName()}"
        val loggerFieldName = aspectContext.fieldFactory.constructorInitialized(
            loggerType,
            CodeBlock.of("%N.getLogger(%S)", loggerFactoryFieldName, loggerName)
        )

        val result = CodeBlock.builder()
        result.generateInputLog(aspectContext, loggerFieldName, function)
        result.generateOutputLog(aspectContext, loggerFieldName, function, superCall)

        return KoraAspect.ApplyResult.MethodBody(result.build())
    }

    private fun CodeBlock.Builder.generateInputLog(aspectContext: KoraAspect.AspectContext, loggerName: String, function: KSFunctionDeclaration) {
        val inLogLevel = function.inLogLevel()
        if (inLogLevel == null) {
            return
        }
        fun CodeBlock.Builder.logInput() {
            addStatement("%L.%L(%S)", loggerName, inLogLevel.logMethod(), MESSAGE_IN)
        }
        if (function.parameters.isEmpty()) {
            logInput()
            return
        }
        val loggedParameters = function.parameters.filter { !it.isAnnotationPresent(logOffAnnotation) }
        if (loggedParameters.isEmpty()) {
            logInput()
            return
        }
        val parametersByLevel = loggedParameters.asSequence()
            .groupBy {
                val parameterLogLevel = it.parseLogLevel(logAnnotation) ?: Level.DEBUG
                maxOf(parameterLogLevel, inLogLevel)
            }
            .toSortedMap()

        val minimalParametersLogLevel = parametersByLevel.minOf { it.key }
        controlFlow("if (%N.%N())", loggerName, minimalParametersLogLevel.isEnabledMethod()) {
            controlFlow("val %N = %T.marker(%S) { gen -> ", DATA_IN_FIELD_NAME, structuredArgument, DATA_PARAMETER_NAME) {
                addStatement("gen.writeStartObject()")
                parametersByLevel.forEach { (level, parameters) ->
                    if (level <= inLogLevel) {
                        parameters.forEach { parameter ->
                            val mapping = parameter.parseMappingData().getMapping(structuredArgumentMapper)
                            val mapperType = mapping?.mapper?.let {
                                if (mapping.isGeneric()) mapping.parameterized(parameter.type.resolve().toTypeName()) else it.toTypeName()
                            } ?: structuredArgumentMapper.parameterizedBy(parameter.type.resolve().toTypeName())
                            val mapper = aspectContext.fieldFactory.constructorParam(mapperType.copy(true), listOf())

                            writeWithMapper(mapper, parameter.name!!.asString(), parameter.name!!.asString())
                        }
                    } else {
                        controlFlow("if (%N.%N())", loggerName, level.isEnabledMethod()) {
                            parameters.forEach { parameter ->
                                val mapping = parameter.parseMappingData().getMapping(structuredArgumentMapper)
                                val mapperType = mapping?.mapper?.let {
                                    if (mapping.isGeneric()) mapping.parameterized(parameter.type.resolve().toTypeName()) else it.toTypeName()
                                } ?: structuredArgumentMapper.parameterizedBy(parameter.type.resolve().toTypeName())
                                val mapper = aspectContext.fieldFactory.constructorParam(mapperType.copy(true), listOf())

                                writeWithMapper(mapper, parameter.name!!.asString(), parameter.name!!.asString())
                            }
                        }
                    }
                }
                addStatement("gen.writeEndObject()")
            }
            addStatement("%N.%N(%L, %S)", loggerName, inLogLevel.logMethod(), DATA_IN_FIELD_NAME, MESSAGE_IN)
            if (minimalParametersLogLevel > inLogLevel) {
                nextControlFlow("else") {
                    logInput()
                }
            }
        }
    }

    private fun CodeBlock.Builder.generateOutputLog(aspectContext: KoraAspect.AspectContext, loggerName: String, function: KSFunctionDeclaration, superCall: String) {
        addStatement("val %L = %L", RESULT_FIELD_NAME, function.superCall(superCall))
        val outLogLevel = function.outLogLevel()
        if (outLogLevel == null) {
            addStatement("return %N", RESULT_FIELD_NAME)
            return
        }
        fun CodeBlock.Builder.logOutput() {
            addStatement("%L.%L(%S)", loggerName, outLogLevel.logMethod(), MESSAGE_OUT)
        }

        val resultLogLevel = function.resultLogLevel()
        if (resultLogLevel == null || function.isVoid()) {
            logOutput()
            addStatement("return %N", RESULT_FIELD_NAME)
            return
        }
        controlFlow("if (%N.%N())", loggerName, resultLogLevel.isEnabledMethod()) {
            controlFlow("val %L = %T.marker(%S) { gen -> ", DATA_OUT_FIELD_NAME, structuredArgument, DATA_PARAMETER_NAME) {
                addStatement("gen.writeStartObject()")
                val mapping = function.parseMappingData().getMapping(structuredArgumentMapper)
                val mapperType = mapping?.mapper?.let {
                    if (mapping.isGeneric()) mapping.parameterized(function.returnType!!.resolve().toTypeName()) else it.toTypeName()
                } ?: structuredArgumentMapper.parameterizedBy(function.returnType!!.resolve().toTypeName())
                val mapper = aspectContext.fieldFactory.constructorParam(mapperType.copy(true), listOf())
                writeWithMapper(mapper, OUT_PARAMETER_NAME, RESULT_FIELD_NAME)
                addStatement("gen.writeEndObject()")
            }
            addStatement("%N.%N(%L, %S)", loggerName, outLogLevel.logMethod(), DATA_OUT_FIELD_NAME, MESSAGE_OUT)
            if (resultLogLevel >= outLogLevel) {
                nextControlFlow("else") {
                    logOutput()
                }
            }
        }
        addStatement("return %N", RESULT_FIELD_NAME)
    }

    private fun KSAnnotated.parseLogLevel(annotation: ClassName): Level? {
        return this.findAnnotation(annotation)
            ?.findValue<KSType>("value")
            ?.declaration?.toString() // ugly enum handling
            ?.let { Level.valueOf(it) }
    }

    private fun KSFunctionDeclaration.inLogLevel(): Level? {
        return this.parseLogLevel(logAnnotation)
            ?: this.parseLogLevel(logInAnnotation)
    }

    private fun KSFunctionDeclaration.outLogLevel(): Level? {
        return this.parseLogLevel(logAnnotation)
            ?: this.parseLogLevel(logOutAnnotation)
            ?: this.parseLogLevel(logResultAnnotation)
    }

    private fun KSFunctionDeclaration.resultLogLevel(): Level? {
        val logOffAnnotation = this.findAnnotation(logOffAnnotation)
        if (logOffAnnotation != null) {
            return null
        }
        val logResultValue = this.parseLogLevel(logResultAnnotation)
        if (logResultValue != null) {
            return logResultValue
        }
        return Level.DEBUG
    }

    private fun CodeBlock.Builder.writeWithMapper(mapperName: String, fieldName: String, parameterName: String) {
        controlFlow("this.%N.let", mapperName) {
            controlFlow("if (it != null)") {
                addStatement("gen.writeFieldName(%S)", fieldName)
                addStatement("it.write(gen, %N)", parameterName)
                nextControlFlow("else")
                addStatement("gen.writeStringField(%S, %L.toString())", fieldName, parameterName)
            }
        }

    }

    private fun Level.logMethod() = this.name.lowercase()
    private fun Level.isEnabledMethod() = "is${this.name.lowercase().replaceFirstChar { c -> c.uppercase() }}Enabled"
}

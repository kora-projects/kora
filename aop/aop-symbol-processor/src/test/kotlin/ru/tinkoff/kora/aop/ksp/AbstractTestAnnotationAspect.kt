package ru.tinkoff.kora.aop.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect.AspectContext
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault

abstract class AbstractTestAnnotationAspect : KoraAspect {
    protected abstract fun testAnnotation(): ClassName

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(testAnnotation().canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: AspectContext): KoraAspect.ApplyResult {
        var annotation = ksFunction.parameters.asSequence()
            .mapNotNull { it.findAnnotation(testAnnotation()) }
            .firstOrNull()
        if (annotation == null) {
            annotation = ksFunction.findAnnotation(testAnnotation())
        }
        if (annotation == null) {
            annotation = ksFunction.parentDeclaration!!.findAnnotation(testAnnotation())!!
        }
        val field: String = aspectContext.fieldFactory.constructorParam(
            TestMethodCallListener::class.asClassName(),
            listOf()
        )

        val annotationValue = annotation.findValueNoDefault<String>("value")
        val b = CodeBlock.builder()
            .addStatement("this.%N.before(%S)", field, annotationValue)
        b.addStatement("var _result: %T = null", ksFunction.returnType?.toTypeName()?.copy(true))
            .beginControlFlow("try")
            .addStatement("_result = %L", superCall(superCall, ksFunction.parameters.map { it.name!!.asString() }))
            .addStatement("this.%N.after(%S, _result)", field, annotationValue)
        b.addStatement("return _result")
        b.nextControlFlow("catch (e: Throwable)")
            .addStatement("this.%N.thrown(%S, e)", field, annotationValue)
            .addStatement("throw e")
            .endControlFlow()

        return KoraAspect.ApplyResult.MethodBody(b.build())
    }
}

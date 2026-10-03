package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isDeferred
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException

class BulkheadKoraAspect(private val resolver: Resolver) : KoraAspect {
    companion object {
        private val ANNOTATION = ClassName("io.koraframework.resilient.bulkhead.annotation", "Bulkheaded")
        private val BULKHEAD = ClassName("io.koraframework.resilient.bulkhead", "Bulkhead")
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(ANNOTATION.canonicalName)

    override fun apply(method: KSFunctionDeclaration, superCall: String, context: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        val returnType = method.returnType!!.resolve()
        val returnName = returnType.declaration.qualifiedName?.asString()
        val superTypes = (returnType.declaration as? KSClassDeclaration)?.getAllSuperTypes()
            ?.map { it.declaration.qualifiedName?.asString() }?.toSet() ?: emptySet()
        val stage = method.isCompletionStage() || "java.util.concurrent.CompletionStage" in superTypes
        if (method.isFuture() || method.isDeferred() || method.isMono() || method.isFlux()
            || (!stage && "java.util.concurrent.Future" in superTypes)
            || "org.reactivestreams.Publisher" == returnName || "org.reactivestreams.Publisher" in superTypes
            || "java.util.concurrent.Flow.Publisher" == returnName || "java.util.concurrent.Flow.Publisher" in superTypes
            || (stage && returnName != "java.util.concurrent.CompletionStage" && returnName != "java.util.concurrent.CompletableFuture")
        ) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Bulkheaded", method, returnType), method)
        }
        val type = method.findAnnotation(ANNOTATION)!!.findValue<KSType>("value")!!
        val contract = resolver.getClassDeclarationByName(BULKHEAD.canonicalName)!!.asStarProjectedType()
        if (!contract.isAssignableFrom(type)) {
            throw ProcessingErrorException(invalidResilientContractError("@Bulkheaded", method, BULKHEAD.canonicalName), method)
        }
        val field = context.fieldFactory.constructorParam(type.toTypeName(), listOf())
        val call = CodeBlock.of("%L(%L)", superCall, method.parameters.joinToString(", ") { it.name!!.asString() })
        val body = CodeBlock.builder()
        if (stage) {
            val resultType = returnType.arguments.first().type?.resolve()?.toTypeName()
                ?: throw ProcessingErrorException(unsupportedReturnTypeError("@Bulkheaded", method, returnType), method)
            body.add("return %L.executeAsync<%T, Throwable> { %L }", field, resultType, call)
            if (returnName == "java.util.concurrent.CompletableFuture") {
                body.add(".toCompletableFuture()")
            }
            body.add("\n")
        } else if (method.isFlow() || Modifier.SUSPEND in method.modifiers) {
            if (method.isFlow()) {
                body.beginControlFlow("return %M", MemberName("kotlinx.coroutines.flow", "flow"))
            }
            body.addStatement("val _admission = %L.acquireAsync().toCompletableFuture()", field)
            body.add("val _permit = %M<%T.Permit> { _continuation ->\n", MemberName("kotlinx.coroutines", "suspendCancellableCoroutine"), BULKHEAD).indent()
            body.addStatement("_continuation.invokeOnCancellation { _admission.cancel(false) }")
            body.add("_admission.whenComplete { _value, _error ->\n").indent()
            body.beginControlFlow("if (_error != null)")
            body.addStatement("_continuation.resumeWith(Result.failure(_error))")
            body.nextControlFlow("else")
            body.add("_continuation.resume(_value) { _cause ->\n").indent()
            body.beginControlFlow("try")
            body.addStatement("_value.observeError(_cause)")
            body.nextControlFlow("finally")
            body.addStatement("_value.close()")
            body.endControlFlow()
            body.endControlFlow()
            body.endControlFlow()
            body.endControlFlow()
            body.endControlFlow()
            body.beginControlFlow(if (method.isFlow()) "try" else "return try")
            if (method.isFlow()) {
                body.addStatement("%M(%L)", MemberName("kotlinx.coroutines.flow", "emitAll"), call)
            } else {
                body.addStatement("%L", call)
                if (method.isVoid()) {
                    body.addStatement("Unit")
                }
            }
            body.nextControlFlow("catch (_error: Throwable)")
            body.addStatement("_permit.observeError(_error)")
            body.addStatement("throw _error")
            body.nextControlFlow("finally")
            body.addStatement("_permit.close()")
            body.endControlFlow()
            if (method.isFlow()) {
                body.endControlFlow()
            }
        } else {
            body.beginControlFlow("return %L.execute<%T, Throwable>", field, returnType.toTypeName())
            body.addStatement("%L", call)
            if (method.isVoid()) {
                body.addStatement("Unit")
            }
            body.endControlFlow()
        }
        return KoraAspect.ApplyResult.MethodBody(body.build())
    }
}

package ru.tinkoff.kora.validation.symbol.processor.aop

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.validation.symbol.processor.*
import java.util.*
import java.util.concurrent.Future

class ValidateMethodKoraAspect(private val resolver: Resolver) : KoraAspect {

    private val validateType = ClassName.bestGuess("ru.tinkoff.kora.validation.common.annotation.Validate")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(validateType.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        val validationOutputCode = buildValidationOutputCode(method, aspectContext)
        if (validationOutputCode != null) {
            if (method.isFuture()) {
                throw ProcessingErrorException("@Validate Return Value can't be applied for types assignable from ${Future::class.java}", method)
            } else if (method.isMono()) {
                throw ProcessingErrorException("@Validate Return Value can't be applied for types assignable from ${Mono::class.java}", method)
            } else if (method.isFlux()) {
                throw ProcessingErrorException("@Validate Return Value can't be applied for types assignable from ${Flux::class.java}", method)
            } else if (method.isVoid()) {
                throw ProcessingErrorException("@Validate Return Value can't be applied for types assignable from ${Void::class.java}", method)
            }
        }

        val validationInputCode = buildValidationInputCode(method, aspectContext)
        if (validationOutputCode == null && validationInputCode == null) {
            return KoraAspect.ApplyResult.Noop.INSTANCE
        }

        val body = if (method.isFlow()) {
            buildBodyFlow(method, superCall, validationOutputCode, validationInputCode)
        } else if (method.isSuspend()) {
            buildBodySuspend(method, superCall, validationOutputCode, validationInputCode)
        } else {
            buildBodySync(method, superCall, validationOutputCode, validationInputCode)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildValidationOutputCode(
        method: KSFunctionDeclaration,
        aspectContext: KoraAspect.AspectContext
    ): CodeBlock? {
        val returnTypeReference = if (method.isFlow())
            method.returnType!!.resolve().arguments.first().type!!
        else
            method.returnType!!

        val constraints = ValidUtils.getConstraints(returnTypeReference, method.annotations)
        val validates = if (method.annotations.any { a -> a.annotationType.resolve().declaration.qualifiedName!!.asString() == VALID_TYPE.canonicalName })
            listOf(Validated(returnTypeReference.resolve().makeNullable().asType()))
        else
            emptyList()

        if (constraints.isEmpty() && validates.isEmpty()) {
            return null
        }

        val isNullable = if (method.isFlow())
            method.returnType!!.resolve().arguments.first().type!!.resolve().isMarkedNullable
        else
            method.returnType!!.resolve().isMarkedNullable

        val builder = CodeBlock.builder()
        if (isNullable) {
            builder.beginControlFlow("if(_result != null)")
        }

        val failFast = method.annotations
            .filter { a -> a.annotationType.resolve().declaration.qualifiedName!!.asString() == VALIDATE_TYPE.canonicalName }
            .flatMap { a ->
                a.arguments
                    .filter { arg -> arg.name!!.asString() == "failFast" }
                    .map { arg -> arg.value ?: false }
                    .map { it as Boolean }
            }
            .firstOrNull() ?: false

        builder
            .addStatement("val _returnValueViolations = %T<%T>(%L)", ArrayList::class.java, VIOLATION_TYPE, constraints.size + validates.size + 1)
            .addStatement("val _returnValueContext = %T.builder().failFast(%L).build()", CONTEXT_TYPE, failFast)

        for (constraint in constraints) {
            val factoryType = constraint.factory.type.asKSType(resolver)
            val constraintFactory = aspectContext.fieldFactory.constructorParam(factoryType, listOf())
            val constraintType = constraint.factory.validator().asKSType(resolver)

            val parameters = CodeBlock.of(constraint.factory.parameters.values.asSequence()
                .map { fp -> CodeBlock.of("%L", fp) }
                .joinToString(", ", "(", ")"))

            val createCodeBlock = CodeBlock.builder()
                .add("%N.create", constraintFactory)
                .add(parameters)
                .build()

            val constraintField = aspectContext.fieldFactory.constructorInitialized(constraintType, createCodeBlock)
            builder.addStatement("_returnValueViolations.addAll(%N.validate(_result, _returnValueContext))", constraintField)

            if (failFast) {
                builder.beginControlFlow("if (!_returnValueViolations.isEmpty())")
                    .addStatement("throw %T(_returnValueViolations)", EXCEPTION_TYPE)
                    .endControlFlow()
            }
        }

        for (validated in validates) {
            val validatorType = validated.validator().asKSType(resolver)
            val validatorField = aspectContext.fieldFactory.constructorParam(validatorType, listOf())
            builder.addStatement("_returnValueViolations.addAll(%N.validate(_result, _returnValueContext))", validatorField)

            if (failFast) {
                builder.beginControlFlow("if (!_returnValueViolations.isEmpty())")
                    .addStatement("throw %T(_returnValueViolations)", EXCEPTION_TYPE)
                    .endControlFlow()
            }
        }

        if(!failFast) {
            builder.beginControlFlow("if (!_returnValueViolations.isEmpty())")
                .addStatement("throw %T(_returnValueViolations)", EXCEPTION_TYPE)
                .endControlFlow()
        }

        if (isNullable) {
            builder.endControlFlow()
        }

        return builder.build()
    }

    private fun buildValidationInputCode(
        method: KSFunctionDeclaration,
        aspectContext: KoraAspect.AspectContext
    ): CodeBlock? {
        if (method.parameters.none { it.isValidatable() }) {
            return null
        }

        val failFast = method.annotations
            .filter { a -> a.annotationType.resolve().declaration.qualifiedName!!.asString() == VALIDATE_TYPE.canonicalName }
            .flatMap { a ->
                a.arguments
                    .filter { arg -> arg.name!!.asString() == "failFast" }
                    .map { arg -> arg.value ?: false }
                    .map { it as Boolean }
            }
            .firstOrNull() ?: false

        val builder = CodeBlock.builder()
            .addStatement("val _argumentsViolations = %T<%T>(%L)", ArrayList::class.java, VIOLATION_TYPE, method.parameters.size * 2)
            .addStatement("val _argumentsContext = %T.builder().failFast(%L).build()", CONTEXT_TYPE, failFast)

        for (parameter in method.parameters.filter { it.isValidatable() }) {
            val isNullable = parameter.type.resolve().isMarkedNullable
            val constraints = ValidUtils.getConstraints(parameter.type, parameter.annotations)
            for (constraint in constraints) {
                val factoryType = constraint.factory.type.asKSType(resolver)
                val constraintFactory = aspectContext.fieldFactory.constructorParam(factoryType, listOf())
                val constraintType = constraint.factory.validator().asKSType(resolver)

                val parameters = CodeBlock.of(constraint.factory.parameters.values.asSequence()
                    .map { fp -> CodeBlock.of("%L", fp) }
                    .joinToString(", ", "(", ")"))

                val createCodeBlock = CodeBlock.builder()
                    .add("%N.create", constraintFactory)
                    .add(parameters)
                    .build()

                val constraintField = aspectContext.fieldFactory.constructorInitialized(constraintType, createCodeBlock)
                if (isNullable) {
                    builder.beginControlFlow("if(%N != null)", parameter.name!!.asString())
                    builder.addStatement("_argumentsViolations.addAll(%N.validate(%N, _argumentsContext))", constraintField, parameter.name!!.asString())
                    builder.endControlFlow()
                } else {
                    builder.addStatement("_argumentsViolations.addAll(%N.validate(%N, _argumentsContext))", constraintField, parameter.name!!.asString())
                }
            }

            val validates = getValidForArguments(parameter)
            for (validated in validates) {
                val validatorType = validated.validator().asKSType(resolver)
                val validatorField = aspectContext.fieldFactory.constructorParam(validatorType, listOf())
                if (isNullable) {
                    builder.beginControlFlow("if(%N != null)", parameter.name!!.asString())
                    builder.addStatement("_argumentsViolations.addAll(%N.validate(%N, _argumentsContext))", validatorField, parameter.name!!.asString())
                    builder.endControlFlow()
                } else {
                    builder.addStatement("_argumentsViolations.addAll(%N.validate(%N, _argumentsContext))", validatorField, parameter.name!!.asString())
                }
            }

            if (failFast) {
                builder.beginControlFlow("if (!_argumentsViolations.isEmpty())")
                    .addStatement("throw %T(_argumentsViolations)", EXCEPTION_TYPE)
                    .endControlFlow()
            }
        }

        if (!failFast) {
            builder.beginControlFlow("if (!_argumentsViolations.isEmpty())")
                .addStatement("throw %T(_argumentsViolations)", EXCEPTION_TYPE)
                .endControlFlow()
        }

        return builder.build()
    }

    private fun KSValueParameter.isValidatable(): Boolean {
        for (annotation in this.annotations) {
            val annotationType = annotation.annotationType.resolve()
            if (annotationType.declaration.qualifiedName!!.asString() == VALID_TYPE.canonicalName) {
                return true
            }

            for (innerAnnotation in annotationType.declaration.annotations) {
                if (innerAnnotation.annotationType.resolve().declaration.qualifiedName!!.asString() == VALIDATED_BY_TYPE.canonicalName) {
                    return true
                }
            }
        }
        return false
    }

    private fun getValidForArguments(parameter: KSValueParameter): List<Validated> {
        return if (parameter.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == VALID_TYPE.canonicalName }) {
            listOf(Validated(parameter.type.resolve().makeNullable().asType()))
        } else
            emptyList()
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration,
        superCall: String,
        validationOutput: CodeBlock?,
        validationInput: CodeBlock?
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val builder = CodeBlock.builder()
        if (method.isVoid()) {
            if (validationInput != null) {
                builder.add(validationInput).add("\n")
            }

            builder.add("%L\n\n".trimIndent(), superMethod.toString())

            if (validationOutput != null) {
                builder.add(validationOutput)
            }
        } else {
            if (validationInput != null) {
                builder.add(validationInput).add("\n")
            }

            builder.add("val _result = %L\n\n".trimIndent(), superMethod.toString())

            if (validationOutput != null) {
                builder.add(validationOutput)
            }

            builder.add("return _result")
        }

        return builder.build()
    }

    private fun buildBodySuspend(
        method: KSFunctionDeclaration,
        superCall: String,
        validationOutput: CodeBlock?,
        validationInput: CodeBlock?
    ): CodeBlock {
        return buildBodySync(method, superCall, validationOutput, validationInput)
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration,
        superCall: String,
        validationOutput: CodeBlock?,
        validationInput: CodeBlock?
    ): CodeBlock {
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val mapMember = MemberName("kotlinx.coroutines.flow", "map")
        val emitAllMember = MemberName("kotlinx.coroutines.flow", "emitAll")

        val superMethod = buildMethodCall(method, superCall)
        val builder = if (validationInput != null)
            CodeBlock.builder()
                .beginControlFlow("return %M", flowMember)
                .add(validationInput)
                .add("%M(%L)\n", emitAllMember, superMethod.toString())
                .endControlFlow()
        else
            CodeBlock.builder()
                .add("return %L\n", superMethod.toString())

        if (validationOutput != null) {
            builder
                .beginControlFlow(".%M", mapMember)
                .add("val _result = it\n")
                .add(validationOutput)
                .add("_result\n")
                .endControlFlow()
        }

        return builder.build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}

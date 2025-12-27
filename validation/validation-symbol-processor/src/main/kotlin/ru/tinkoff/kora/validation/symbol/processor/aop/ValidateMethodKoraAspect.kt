package ru.tinkoff.kora.validation.symbol.processor.aop

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotations
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.resolveToUnderlying
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.CONTEXT_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.EXCEPTION_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALIDATED_BY_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALIDATE_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VALID_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidTypes.VIOLATION_TYPE
import ru.tinkoff.kora.validation.symbol.processor.ValidUtils.getConstraints
import ru.tinkoff.kora.validation.symbol.processor.Validated
import ru.tinkoff.kora.validation.symbol.processor.asType
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class ValidateMethodKoraAspect(private val resolver: Resolver) : KoraAspect {

    private val validateType = ClassName.bestGuess("ru.tinkoff.kora.validation.common.annotation.Validate")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(validateType.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        val validationOutputCode = buildValidationOutputCode(ksFunction, aspectContext)
        if (validationOutputCode != null) {
            if (ksFunction.isFuture()) {
                throw ProcessingErrorException("@Validate return value can't be applied for types assignable from ${Future::class.java}", ksFunction)
            } else if (ksFunction.isCompletionStage()) {
                throw ProcessingErrorException("@Validate return value can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
            } else if (ksFunction.isMono()) {
                throw ProcessingErrorException("@Validate return value can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
            } else if (ksFunction.isFlux()) {
                throw ProcessingErrorException("@Validate return value can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
            } else if (ksFunction.isVoid()) {
                throw ProcessingErrorException("@Validate return value can't be applied for types assignable from ${Void::class.java}", ksFunction)
            }
        }

        val validationInputCode = buildValidationInputCode(ksFunction, aspectContext)
        if (validationOutputCode == null && validationInputCode == null) {
            return KoraAspect.ApplyResult.Noop.INSTANCE
        }

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, validationOutputCode, validationInputCode)
        } else if (ksFunction.isSuspend()) {
            buildBodySync(ksFunction, superCall, validationOutputCode, validationInputCode)
        } else {
            buildBodySync(ksFunction, superCall, validationOutputCode, validationInputCode)
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

        val constraints = method.getConstraints()
        val validates = if (method.isAnnotationPresent(VALID_TYPE)) {
            listOf(Validated(returnTypeReference.resolve().makeNullable().asType()))
        } else {
            emptyList()
        }

        val resolvedType = returnTypeReference.resolve()
        val isNullable = resolvedType.isMarkedNullable
        val isNotNull = (method.annotations + (method.returnType?.annotations ?: emptySequence()))
            .map { it.annotationType.resolveToUnderlying().toClassName().simpleName }
            .any { it.contentEquals("NonNull", true) || it.contentEquals("NotNull", true) }
        val isJsonNullable = resolvedType.declaration.let { if (it is KSClassDeclaration) it.toClassName() else null } == ValidTypes.jsonNullable

        if (constraints.isEmpty() && validates.isEmpty() && !(isJsonNullable && isNotNull)) {
            return null
        }

        val memberList = MemberName("kotlin.collections", "mutableListOf")
        val builder = CodeBlock.builder()

        if (isJsonNullable && isNotNull && isNullable && constraints.isEmpty() && validates.isEmpty()) {
            builder.beginControlFlow("if(_result == null || !_result.isDefined() || _result.isNull())")
        } else if (isJsonNullable && isNotNull && constraints.isEmpty() && validates.isEmpty()) {
            builder.beginControlFlow("if(!_result.isDefined() || _result.isNull())")
        } else if (isJsonNullable && isNullable && isNotNull) {
            builder.beginControlFlow("if(%_result != null && _result.isDefined() && !_result.isNull())")
        } else if (isJsonNullable && isNullable) {
            builder.beginControlFlow("if(_result != null && _result.isDefined())")
        } else if (isJsonNullable) {
            builder.beginControlFlow("if(_result.isDefined())")
        } else if (isNullable) {
            builder.beginControlFlow("if(_result != null)")
        }

        val failFast = method.findAnnotations(VALIDATE_TYPE)
            .flatMap { a ->
                a.arguments
                    .filter { arg -> arg.name?.asString() == "failFast" }
                    .map { arg -> arg.value ?: false }
                    .map { it as Boolean }
            }.firstOrNull() ?: false

        if (failFast) {
            builder.addStatement("val _returnContext = %T.failFast()", CONTEXT_TYPE)
        } else {
            builder.addStatement("val _returnContext = %T.full()", CONTEXT_TYPE)
        }

        val returnAccessor = if (isJsonNullable) "_result.value()" else "_result"
        if (!failFast) {
            builder.addStatement("val _returnViolations = %M<%T>()", memberList, VIOLATION_TYPE)
        }

        for ((i, constraint) in constraints.withIndex()) {
            val factoryType = constraint.factory.type.asKSType(resolver)
            val constraintFactory = aspectContext.fieldFactory.constructorParam(factoryType, listOf())
            val constraintType = constraint.factory.validator().asKSType(resolver)

            val parameters = CodeBlock.of(
                constraint.factory.parameters.values.asSequence()
                .map {
                    when (it) {
                        is String -> CodeBlock.of("%S", it)
                        is KSClassDeclaration if it.classKind == ClassKind.ENUM_ENTRY -> CodeBlock.of("%T.%N", (it.parentDeclaration as KSClassDeclaration).toClassName(), it.simpleName.asString())
                        else -> CodeBlock.of("%L", it)
                    }
                }
                .joinToString(", ", "(", ")"))


            val createCodeBlock = CodeBlock.builder()
                .add("%N.create", constraintFactory)
                .add(parameters)
                .build()

            val constraintField = aspectContext.fieldFactory.constructorInitialized(constraintType, createCodeBlock)
            val constraintResultField = "_returnConstResult_${i + 1}"
            if (failFast) {
                builder.addStatement("val %N = %N.validate(%L, _returnContext)", constraintResultField, constraintField, returnAccessor)
                    .beginControlFlow("if (%N.isNotEmpty())", constraintResultField)
                    .addStatement("throw %T(%N)", EXCEPTION_TYPE, constraintResultField)
                    .endControlFlow()
            } else {
                builder.addStatement("_returnViolations.addAll(%N.validate(%L, _returnContext))", constraintField, returnAccessor)
            }
        }

        for ((i, validated) in validates.withIndex()) {
            val validatorType = validated.validator().asKSType(resolver)
            val validatorField = aspectContext.fieldFactory.constructorParam(validatorType, listOf())
            val validatorResultField = "_returnValidatorResult_${i + 1}"
            builder.addStatement("val %N = %N.validate(%L, _returnContext)", validatorResultField, validatorField, returnAccessor)
            if (failFast) {
                builder.beginControlFlow("if (%N.isNotEmpty())", validatorResultField)
                    .addStatement("throw %T(%N)", EXCEPTION_TYPE, validatorResultField)
                    .endControlFlow()
            } else {
                builder.addStatement("_returnViolations.addAll(%N)", validatorResultField)
            }
        }

        val errorNullMsg = "Result must be non null, but was null"
        if (isJsonNullable && isNotNull && constraints.isEmpty() && validates.isEmpty()) {
            if (failFast) {
                builder.addStatement("throw %T(_returnContext.violates(%S))", EXCEPTION_TYPE, errorNullMsg)
            } else {
                builder.addStatement("_returnViolations.add(_returnContext.violates(%S))", errorNullMsg)
            }
        }

        if (!failFast) {
            builder.controlFlow("if (_returnViolations.isNotEmpty())") {
                addStatement("throw %T(_returnViolations)", EXCEPTION_TYPE)
            }
        }

        if (isJsonNullable && isNotNull && (constraints.isNotEmpty() || validates.isNotEmpty())) {
            builder.nextControlFlow("else")
            builder.addStatement("throw %T(_returnContext.violates(%S))", EXCEPTION_TYPE, errorNullMsg)
            builder.endControlFlow()
        } else if (isJsonNullable) {
            builder.endControlFlow()
        } else if (isNullable) {
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

        val failFast = method.findAnnotations(VALIDATE_TYPE)
            .flatMap { a ->
                a.arguments
                    .filter { arg -> arg.name!!.asString() == "failFast" }
                    .map { arg -> arg.value ?: false }
                    .map { it as Boolean }
            }
            .firstOrNull() ?: false

        val memberList = MemberName("kotlin.collections", "mutableListOf")
        val builder = if (failFast)
            CodeBlock.builder().addStatement("val _argsContext = %T.failFast()", CONTEXT_TYPE)
        else
            CodeBlock.builder().addStatement("val _argsContext = %T.full()", CONTEXT_TYPE)

        if (!failFast) {
            builder.addStatement("val _argsViolations = %M<%T>()", memberList, VIOLATION_TYPE)
        }
        builder.add("\n")

        for (parameter in method.parameters.filter { it.isValidatable() }) {
            val resolvedType = parameter.type.resolve()
            val isNullable = resolvedType.isMarkedNullable
            val isNotNull = (parameter.annotations + parameter.type.annotations)
                .map { it.annotationType.resolveToUnderlying().toClassName().simpleName }
                .any { it.contentEquals("NonNull", true) || it.contentEquals("NotNull", true) }
            val isJsonNullable = resolvedType.toTypeName().let { it is ParameterizedTypeName && it.rawType == ValidTypes.jsonNullable }

            val constraints = parameter.getConstraints()
            val validates = getValidForArguments(parameter)

            val parameterName = parameter.name!!.asString()
            val parameterAccessor = if (isJsonNullable) "${parameter.name!!.asString()}.value()" else parameter.name!!.asString()
            val argumentContext = "_argsContext_" + parameterName

            if (isJsonNullable && isNotNull && isNullable && constraints.isEmpty() && validates.isEmpty()) {
                builder.beginControlFlow("if(%N == null || !%N.isDefined() || %N.isNull())", parameterName, parameterName, parameterName)
            } else if (isJsonNullable && isNotNull && constraints.isEmpty() && validates.isEmpty()) {
                builder.beginControlFlow("if(!%N.isDefined() || %N.isNull())", parameterName, parameterName)
            } else if (isJsonNullable && isNullable && isNotNull) {
                builder.beginControlFlow("if(%N != null && %N.isDefined() && !%N.isNull())", parameterName, parameterName, parameterName)
            } else if (isJsonNullable && isNullable) {
                builder.beginControlFlow("if(%N != null && %N.isDefined())", parameterName, parameterName)
            } else if (isJsonNullable) {
                builder.beginControlFlow("if(%N.isDefined())", parameterName)
            } else if (isNullable) {
                builder.beginControlFlow("if(%N != null)", parameterName)
            }

            builder.addStatement(
                "val %N = _argsContext.addPath(%S)",
                argumentContext, parameterName
            )

            for ((i, constraint) in constraints.withIndex()) {
                val factoryType = constraint.factory.type.asKSType(resolver)
                val constraintFactory = aspectContext.fieldFactory.constructorParam(factoryType, listOf())
                val constraintType = constraint.factory.validator().asKSType(resolver)

                val parameters = CodeBlock.of(
                    constraint.factory.parameters.values.asSequence()
                    .map {
                        when (it) {
                            is String -> CodeBlock.of("%S", it)
                            is KSClassDeclaration if it.classKind == ClassKind.ENUM_ENTRY -> CodeBlock.of(
                                "%T.%N",
                                (it.parentDeclaration as KSClassDeclaration).toClassName(),
                                it.simpleName.asString()
                            )

                            else -> CodeBlock.of("%L", it)
                        }
                    }
                    .joinToString(", ", "(", ")"))

                val createCodeBlock = CodeBlock.builder()
                    .add("%N.create", constraintFactory)
                    .add(parameters)
                    .build()

                val constraintField = aspectContext.fieldFactory.constructorInitialized(constraintType, createCodeBlock)
                val constraintResultField = "_argConstResult_${parameterName}_${i + 1}"
                if (failFast) {
                    builder.addStatement("val %N = %N.validate(%L, %N)", constraintResultField, constraintField, parameterAccessor, argumentContext)
                        .beginControlFlow("if(%N.isNotEmpty())", constraintResultField)
                        .addStatement("throw %T(%N)", EXCEPTION_TYPE, constraintResultField)
                        .endControlFlow()
                } else {
                    builder.addStatement("_argsViolations.addAll(%N.validate(%L, %N))", constraintField, parameterAccessor, argumentContext)
                }
            }

            for ((i, validated) in validates.withIndex()) {
                val validatorType = validated.validator().asKSType(resolver)
                val validatorField = aspectContext.fieldFactory.constructorParam(validatorType, listOf())
                val validatorResultField = "_argValidResult_${parameterName}_${i + 1}"

                if (failFast) {
                    builder.addStatement("val %N = %N.validate(%L, %N)", validatorResultField, validatorField, parameterAccessor, argumentContext)
                        .beginControlFlow("if(%N.isNotEmpty())", validatorResultField)
                        .addStatement("throw %T(%N)", EXCEPTION_TYPE, validatorResultField)
                        .endControlFlow()
                } else {
                    builder.addStatement("_argsViolations.addAll(%N.validate(%L, %N))", validatorField, parameterAccessor, argumentContext)
                }
            }

            if (isJsonNullable && isNotNull) {
                if (constraints.isNotEmpty() || validates.isNotEmpty()) {
                    builder.nextControlFlow("else")
                    builder.addStatement("val %N = _argsContext.addPath(%S)", argumentContext, parameterName)
                }
                val errorMsg = "Parameter '${parameterName}' must be non null, but was null"
                if (failFast) {
                    builder.addStatement("throw %T(%N.violates(%S))", EXCEPTION_TYPE, argumentContext, errorMsg)
                } else {
                    builder.addStatement("_argsViolations.add(%N.violates(%S))", argumentContext, errorMsg)
                }
                builder.endControlFlow()
            } else if (isJsonNullable) {
                builder.endControlFlow()
            } else if (isNullable) {
                builder.endControlFlow()
            }
        }

        if (!failFast) {
            builder.add("\n")
                .controlFlow("if (_argsViolations.isNotEmpty())") {
                    addStatement("throw %T(_argsViolations)", EXCEPTION_TYPE)
                }
        }

        return builder.build()
    }

    private fun KSValueParameter.isValidatable(): Boolean {
        for (annotation in this.annotations) {
            val annotationType = annotation.annotationType.resolve()
            if (annotationType.declaration.qualifiedName?.asString() == VALID_TYPE.canonicalName) {
                return true
            }

            for (innerAnnotation in annotationType.declaration.annotations) {
                if (innerAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == VALIDATED_BY_TYPE.canonicalName) {
                    return true
                }
            }
        }

        val resolvedType = type.resolve()
        val isNotNull = (annotations + type.annotations)
            .map { it.annotationType.resolveToUnderlying().toClassName().simpleName }
            .any { it.contentEquals("NonNull", true) || it.contentEquals("NotNull", true) }
        val isJsonNullable = resolvedType.declaration.let { it is KSClassDeclaration && it.toClassName() == ValidTypes.jsonNullable }
        return isJsonNullable && isNotNull
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

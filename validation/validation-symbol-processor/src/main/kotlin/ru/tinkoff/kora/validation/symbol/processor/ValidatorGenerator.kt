package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.collectFinalSealedSubtypes
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.ksp.common.visitClass
import ru.tinkoff.kora.validation.symbol.processor.ValidUtils.getConstraints

class ValidatorGenerator(val codeGenerator: CodeGenerator) {
    fun getValidatorSpec(meta: ValidatorMeta): ValidSymbolProcessor.ValidatorSpec {
        val parameterSpecs = ArrayList<ParameterSpec>()
        val typeName = meta.validator.contract
        val validatorSpecBuilder = TypeSpec.classBuilder(meta.sourceDeclaration.generatedClassName("Validator"))
            .addSuperinterface(typeName)
            .addAnnotation(
                AnnotationSpec.builder(CommonClassNames.generated)
                    .addMember("%S", this.javaClass.canonicalName)
                    .build()
            )

        val constraintToFieldName = HashMap<Constraint.Factory, String>()
        val validatedToFieldName = HashMap<Validated, String>()
        val constraintBuilder = ArrayList<CodeBlock>()
        for (i in meta.fields.indices) {
            val field = meta.fields[i]
            val contextField = "_context$i"
            constraintBuilder.add(
                CodeBlock.of(
                    """
                        
                    val %L = context.addPath(%S)
                    
                    """.trimIndent(), contextField, field.name
                )
            )

            for (j in field.constraint.indices) {
                val constraint = field.constraint[j]
                val suffix = i.toString() + "_" + j
                val constraintField = constraintToFieldName.computeIfAbsent(constraint.factory) { "_constraint$suffix" }
                val constraintResultField = "_constraintResult_$suffix"

                constraintBuilder.add(
                    CodeBlock.of(
                        """
                        val %N = %L.validate(value.%L, %L)
                        if(context.isFailFast && %N.isNotEmpty()) {
                            return %N
                        }
                        _violations.addAll(%N)
                        
                        """.trimIndent(),
                        constraintResultField, constraintField, field.accessor(), contextField, constraintResultField, constraintResultField, constraintResultField
                    )
                )
            }

            for (j in field.validates.indices) {
                val validated = field.validates[j]
                val suffix = i.toString() + "_" + j
                val validatorField = validatedToFieldName.computeIfAbsent(validated) { "_validator$suffix" }
                val validatorResultField = "_validatorResult_$suffix"
                if (field.isNotNull()) {
                    constraintBuilder.add(
                        CodeBlock.of(
                            """
                            val %N = %L.validate(value.%L, %L)
                            if(context.isFailFast && %N.isNotEmpty()) {
                                return %N
                            }
                            _violations.addAll(%N)
                            
                            """.trimIndent(),
                            validatorResultField, validatorField, field.accessor(), contextField, validatorResultField, validatorResultField, validatorResultField
                        )
                    )
                } else {
                    constraintBuilder.add(
                        CodeBlock.of(
                            """
                            if(value.%L != null) {
                                val %N = %L.validate(value.%L, %L)
                                if(context.isFailFast && %N.isNotEmpty()) {
                                    return %N
                                }
                                _violations.addAll(%N)
                            }
                            
                            """.trimIndent(),
                            field.accessor(), validatorResultField, validatorField, field.accessor(), contextField, validatorResultField, validatorResultField, validatorResultField
                        )
                    )
                }
            }
        }

        val constructorSpecBuilder = FunSpec.constructorBuilder().addModifiers(KModifier.PUBLIC)
        for (entry in constraintToFieldName) {
            val factory = entry.key
            val fieldName = entry.value
            val validatorType = factory.validator()
            val createParameters = factory.parameters.values.joinToString(", ") {
                if (it is String) {
                    CodeBlock.of("%S", it).toString()
                } else {
                    CodeBlock.of("%L", it).toString()
                }
            }

            validatorSpecBuilder.addProperty(
                PropertySpec.builder(
                    fieldName,
                    validatorType.asPoetType(),
                    KModifier.PRIVATE
                ).build()
            )

            val parameterSpec = ParameterSpec.builder(fieldName, factory.type.asPoetType()).build()
            parameterSpecs.add(parameterSpec)
            constructorSpecBuilder
                .addParameter(parameterSpec)
                .addStatement("this.%L = %L.create(%L)", fieldName, fieldName, createParameters)
        }

        for (entry in validatedToFieldName) {
            val fieldName = entry.value
            val fieldType = entry.key.validator().asPoetType()
            PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE).build();
            validatorSpecBuilder.addProperty(PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE).build())
            val parameterSpec = ParameterSpec.builder(fieldName, fieldType).build()
            parameterSpecs.add(parameterSpec)
            constructorSpecBuilder
                .addParameter(parameterSpec)
                .addStatement("this.%L = %L", fieldName, fieldName)
        }

        val memberList = MemberName("kotlin.collections", "mutableListOf")
        val validateMethodSpecBuilder = FunSpec.builder("validate")
            .addModifiers(KModifier.OVERRIDE)
            .returns("kotlin.collections.MutableList".asType(listOf(VIOLATION_TYPE.canonicalName.asType())).asPoetType())
            .addParameter(ParameterSpec.builder("value", meta.source.copy(true)).build())
            .addParameter(ParameterSpec.builder("context", CONTEXT_TYPE.canonicalName.asType().asPoetType()).build())
            .addCode(
                CodeBlock.of(
                    """
                    if(value == null) {
                        return mutableListOf(context.violates("Input value is null"))
                    }
                    
                    val _violations = %M<Violation>()
                    
                    """.trimIndent(), memberList
                )
            )

        constraintBuilder.forEach { b -> validateMethodSpecBuilder.addCode(b) }
        validateMethodSpecBuilder.addCode(CodeBlock.of("return _violations"))

        val typeSpec = validatorSpecBuilder
            .addFunction(constructorSpecBuilder.build())
            .addFunction(validateMethodSpecBuilder.build())
            .build()

        return ValidSymbolProcessor.ValidatorSpec(meta, typeSpec, parameterSpecs)
    }

    private fun getValidatorMeta(declaration: KSClassDeclaration): ValidatorMeta {
        if (declaration.classKind == ClassKind.INTERFACE || declaration.classKind == ClassKind.ENUM_CLASS) {
            throw ProcessingErrorException("Validation can't be generated for: ${declaration.classKind}", declaration)
        }

        val elementFields = declaration.getAllProperties()
            .filter { p -> !p.modifiers.contains(Modifier.JAVA_STATIC) }
            .filter { p -> !p.modifiers.contains(Modifier.CONST) }
            .toList()

        val fields = ArrayList<Field>()
        for (fieldProperty in elementFields) {
            val constraints = fieldProperty.getConstraints()
            val validateds = getValid(fieldProperty)
            val isNullable = fieldProperty.type.resolve().isMarkedNullable
            if (constraints.isNotEmpty() || validateds.isNotEmpty()) {
                fields.add(
                    Field(
                        fieldProperty.type.asType(),
                        fieldProperty.simpleName.asString(),
                        declaration.modifiers.any { m -> m == Modifier.DATA },
                        isNullable,
                        constraints,
                        validateds
                    )
                )
            }
        }

        val source = declaration.asType(listOf()).toTypeName()
        return ValidatorMeta(
            source,
            declaration,
            ValidatorType(
                VALIDATOR_TYPE.parameterizedBy(declaration.asType(listOf()).toTypeName().copy(false))
            ),
            fields
        )
    }

    private fun getValid(field: KSPropertyDeclaration): List<Validated> {
        if (field.isAnnotationPresent(VALID_TYPE)) {
            return listOf(Validated(field.type.asType()))
        }
        val parentClass = field.parentDeclaration as KSClassDeclaration
        parentClass.primaryConstructor?.parameters
            ?.filter { it.name?.asString() == field.simpleName.asString() }
            ?.firstOrNull { it.isAnnotationPresent(VALID_TYPE) }
            ?.let {
                return listOf(Validated(field.type.asType()))
            }

        return emptyList()
    }

    fun generate(symbol: KSAnnotated) {
        if (symbol is KSClassDeclaration && symbol.classKind == ClassKind.INTERFACE && symbol.modifiers.contains(Modifier.SEALED)) {
            return this.generateForSealed(symbol)
        }
        val meta = symbol.visitClass { clazz -> getValidatorMeta(clazz) }
        if (meta == null) {
            return
        }
        val validatorSpec = getValidatorSpec(meta)
        val fileSpec = FileSpec.builder(validatorSpec.meta.sourceDeclaration.packageName.asString(), validatorSpec.spec.name!!)
            .addType(validatorSpec.spec)
            .build()

        fileSpec.writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    private fun generateForSealed(symbol: KSClassDeclaration) {
        val typeName = symbol.asType(listOf()).toTypeName()
        val validatorTypeName = VALIDATOR_TYPE.parameterizedBy(typeName.copy(false))
        val validatorSpecBuilder = TypeSpec.classBuilder(symbol.generatedClassName("Validator"))
            .addSuperinterface(validatorTypeName)
            .generated(ValidatorGenerator::class)
        symbol.containingFile?.let(validatorSpecBuilder::addOriginatingKSFile)
        for (typeParameter in symbol.typeParameters) {
            validatorSpecBuilder.addTypeVariable(typeParameter.toTypeVariableName())
        }

        val constructor = FunSpec.constructorBuilder()
        val method = FunSpec.builder("validate")
            .addModifiers(KModifier.OVERRIDE)
            .returns(CommonClassNames.list.parameterizedBy(VIOLATION_TYPE))
            .addParameter("value", typeName.copy(true))
            .addParameter("context", CONTEXT_TYPE)

        for ((i, subclass) in symbol.collectFinalSealedSubtypes().withIndex()) {
            val name = "_validator${i + 1}"
            val subtypeName = subclass.asType(listOf()).toTypeName()
            val fieldValidator = VALIDATOR_TYPE.parameterizedBy(subtypeName.copy(false))
            validatorSpecBuilder.addProperty(name, fieldValidator, KModifier.PRIVATE)
            constructor.addParameter(name, fieldValidator)
                .addStatement("this.%N = %N", name, name)
            if (i > 0) {
                method.nextControlFlow("else if (value is %T)", subtypeName)
            } else {
                method.beginControlFlow("if (value is %T)", subtypeName)
            }
            method.addStatement("return %N.validate(value, context)", name)
        }
        validatorSpecBuilder.addFunction(method.endControlFlow().addStatement("throw %T()", IllegalStateException::class.asClassName()).build())
        validatorSpecBuilder.addFunction(constructor.build())

        val spec = validatorSpecBuilder.build()

        val fileSpec = FileSpec.builder(symbol.packageName.asString(), spec.name!!)
            .addType(spec)
            .build()

        fileSpec.writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}

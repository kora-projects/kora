package ru.tinkoff.kora.openapi.generator.kotlingen

import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeVariableName
import com.palantir.javapoet.WildcardTypeName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.IJsonSchemaValidationProperties
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.AbstractGenerator
import ru.tinkoff.kora.openapi.generator.KoraCodegen


abstract class AbstractKotlinGenerator<C: Any> : AbstractGenerator<C, FileSpec>() {
    protected fun buildAdditionalAnnotations(tag: String): List<AnnotationSpec> {


        var additionalAnnotations = params.additionalContractAnnotations[tag]
        if (additionalAnnotations == null) {
            additionalAnnotations = params.additionalContractAnnotations["*"]
        }
        val result = mutableListOf<AnnotationSpec>()
        for (additionalAnnotation in additionalAnnotations.orEmpty()) {
            if (additionalAnnotation.annotation != null && !additionalAnnotation.annotation.isBlank()) {
                TODO("parse text to to annotation spec")
            }
        }
        return result
    }

    protected fun buildImplicitHeaders(operation: CodegenOperation) = operation.implicitHeadersParams.orEmpty().asSequence()
        .map {
            AnnotationSpec.builder(ClassName("io.swagger.v3.oas.annotations", "Parameter"))
                .addMember("name = %S", it.baseName)
                .addMember("description = %S", it.description ?: "")
                .addMember("required = %L", it.required)
                .addMember("%N = %T.HEADER", "in", ClassName("io.swagger.v3.oas.annotations.enums", "ParameterIn"))
                .build()
        }
        .toList()

    protected fun buildRouteAnnotation(operation: CodegenOperation) = AnnotationSpec.builder(Classes.httpRoute.asKt())
        .addMember("method = %S", operation.httpMethod)
        .addMember("path = %S", operation.path)
        .build()

    protected fun buildFormParamsRecord(ctx: OperationsMap, operation: CodegenOperation): TypeSpec {
        val t = TypeSpec.classBuilder(StringUtils.capitalize(operation.operationId) + "FormParam")
            .addModifiers(KModifier.DATA)
            .addAnnotation(generated())
        val b = FunSpec.constructorBuilder()
        for (formParam in operation.formParams) {
            var type = if (formParam.isFile)
                Classes.formPart.asKt()
            else
                asType(ctx, operation, formParam).asKt()
            if (!formParam.required) {
                type = type.copy(nullable = true)
            }
            val p = ParameterSpec.builder(formParam.paramName, type)
            if (formParam.description != null) {
                p.addKdoc(formParam.description).addKdoc(" ")
            }
            if (formParam.required) {
                p.addKdoc("(required)")
            } else if (formParam.defaultValue != null) {
                p.defaultValue("%L", formParam.defaultValue)
                p.addKdoc("(optional, default to " + formParam.defaultValue + ")")
            } else {
                p.addKdoc("(optional)")
            }
            b.addParameter(p.build())
            t.addProperty(PropertySpec.builder(formParam.paramName, type).initializer(formParam.paramName).build())
        }

        return t
            .primaryConstructor(b.build())
            .build()
    }

    protected fun generated() = AnnotationSpec.builder(Classes.generated.asKt()).addMember("%S", this::class.java.getCanonicalName()).build()

    protected fun buildMethodAuth(operation: CodegenOperation, interceptorType: ClassName): AnnotationSpec? {
        if (params.authAsMethodArgument) {
            // should be handled on parameters level
            return null
        }
        if (!operation.hasAuthMethods) {
            return null
        }
        val authMethod = operation.authMethods.asSequence()
            .filter { a -> params.primaryAuth == null || a.name == params.primaryAuth }
            .firstOrNull()
            ?: throw IllegalArgumentException("Can't find OpenAPI securitySchema named: " + params.primaryAuth)
        val authName = camelize(authMethod.name) // camelize(toVarName(authMethod.name)); todo toVarName

        return AnnotationSpec
            .builder(Classes.interceptWith.asKt())
            .addMember("value = %T::class", interceptorType)
            .addMember("tag = %T(%T::class)", Classes.tag.asKt(), ClassName(apiPackage, "ApiSecurity", authName))
            .build()
    }


    protected fun buildInterceptors(tag: String, defaultInterceptorType: ClassName): List<AnnotationSpec> {
        val interceptors = params.interceptors.getOrDefault(tag, params.interceptors["*"])
        if (interceptors == null) {
            return listOf<AnnotationSpec>()
        }
        val result = mutableListOf<AnnotationSpec>()
        for (interceptor in interceptors) {
            val type = interceptor.type
                ?.let { com.palantir.javapoet.ClassName.bestGuess(it).asKt() }
                ?: defaultInterceptorType
            val interceptorTag = interceptor.tag
            val ann = AnnotationSpec
                .builder(Classes.interceptWith.asKt())
                .addMember("value = %T::class", type)
            if (interceptorTag != null) {
                ann.addMember("tag = %T(%T::class)", Classes.tag.asKt(), com.palantir.javapoet.ClassName.bestGuess(interceptor.tag as String).asKt())
            }
            result.add(ann.build())
        }
        return result
    }


    protected fun buildParameter(ctx: OperationsMap, operation: CodegenOperation, param: CodegenParameter): ParameterSpec {
        var type = asType(ctx, operation, param).asKt()
        if (!param.required) {
            type = type.copy(nullable = true)
        }
        require(!param.isFormParam) { "Form parameters should be handled separately" }
        val b = ParameterSpec.builder(param.paramName, type)
        when {
            param.isQueryParam -> b.addAnnotation(
                AnnotationSpec.builder(Classes.query.asKt())
                    .addMember("value = %S", param.baseName)
                    .build()
            )

            param.isPathParam -> b.addAnnotation(
                AnnotationSpec.builder(Classes.path.asKt())
                    .addMember("value = %S", param.baseName)
                    .build()
            )

            param.isHeaderParam -> b.addAnnotation(
                AnnotationSpec.builder(Classes.header.asKt())
                    .addMember("value = %S", param.baseName)
                    .build()
            )

            param.isCookieParam -> b.addAnnotation(
                AnnotationSpec.builder(Classes.cookie.asKt())
                    .addMember("value = %S", param.baseName)
                    .build()
            )

            param.isBodyParam && KoraCodegen.isContentJson(param) -> b.addAnnotation(
                AnnotationSpec.builder(Classes.json.asKt())
                    .build()
            )
        }
        if (params.codegenMode.isServer && params.enableValidation) {
            val validation = getValidation(param)
            if (validation != null) {
                b.addAnnotation(validation)
            }
        }
        if (params.codegenMode.isClient) {
            if (!param.required) {
                if (param.defaultValue == null) {
                    b.defaultValue("null")
                } else {
                    b.defaultValue(param.defaultValue)
                }
            }
        }
        return b.build()
    }


    protected fun getValidation(variable: IJsonSchemaValidationProperties): AnnotationSpec? {
        if (variable.minimum != null || variable.maximum != null) {
            val minimum = when {
                variable.minimum != null && !variable.minimum.contains(".") -> CodeBlock.of("%L.0", variable.minimum)
                variable.minimum != null -> CodeBlock.of("%L", variable.minimum)
                variable.isLong -> CodeBlock.of("%L.0", Long.MIN_VALUE)
                variable.isInteger -> CodeBlock.of("%L.0", Int.MIN_VALUE)
                variable.isDouble -> CodeBlock.of("%T.MIN_VALUE", DOUBLE)
                variable.isFloat -> CodeBlock.of("%T.MIN_VALUE", FLOAT)
                else -> throw IllegalArgumentException("Invalid minimum variable type value: $variable")
            }
            val maximum = when {
                variable.maximum != null && !variable.maximum.contains(".") -> CodeBlock.of("%L.0", variable.maximum)
                variable.maximum != null -> CodeBlock.of("%L", variable.minimum)
                variable.isLong -> CodeBlock.of("%L.0", Long.MAX_VALUE)
                variable.isInteger -> CodeBlock.of("%L.0", Int.MAX_VALUE)
                variable.isDouble -> CodeBlock.of("%T.MAX_VALUE", DOUBLE)
                variable.isFloat -> CodeBlock.of("%T.MAX_VALUE", FLOAT)
                else -> throw IllegalArgumentException("Invalid minimum variable type value: $variable")
            }
            return AnnotationSpec.builder(Classes.range.asKt())
                .addMember("from = %L", minimum)
                .addMember("to = %L", maximum)
                .addMember(
                    "boundary = %T.%L_%L",
                    Classes.boundary.asKt(),
                    if (variable.exclusiveMinimum) "EXCLUSIVE" else "INCLUSIVE",
                    if (variable.exclusiveMaximum) "EXCLUSIVE" else "INCLUSIVE"
                )
                .build()
        }
        if (variable.minLength != null || variable.maxLength != null) {
            return AnnotationSpec.builder(Classes.size.asKt())
                .addMember("min = %L", variable.minLength ?: 0)
                .addMember("max = %L", variable.maxLength ?: Int.MAX_VALUE)
                .build()
        }
        if (variable.maxItems != null || variable.minItems != null) {
            return AnnotationSpec.builder(Classes.size.asKt())
                .addMember("min = %L", variable.minItems ?: 0)
                .addMember("max = %L", variable.maxItems ?: Int.MAX_VALUE)
                .build()
        }
        if (variable.pattern != null) {
            return AnnotationSpec.builder(Classes.pattern.asKt())
                .addMember("value = %S", variable.pattern)
                .build()
        }
        if (variable.isModel) {
            return AnnotationSpec.builder(Classes.valid.asKt()).build()
        }
        return null
    }


    protected fun buildFunctionKdoc(ctx: OperationsMap, operation: CodegenOperation): CodeBlock {
        val b = CodeBlock.builder()
        b.add(operation.httpMethod + " " + operation.path)
        if (operation.summary != null) {
            b.add(": " + operation.summary)
        }
        b.add("\n")
        if (operation.notes != null) {
            b.add(operation.notes).add("\n")
        }
        for (param in operation.allParams) {
            if (!param.isFormParam) {
                b.add("@param ").add(param.paramName).add(" ")
                if (param.description != null) {
                    b.add(param.description.trim { it <= ' ' })
                } else {
                    b.add(param.baseName)
                }
                if (param.required) {
                    b.add(" (required)")
                } else {
                    b.add(" (optional")
                    if (param.defaultValue != null) {
                        b.add(", default to ").add(param.defaultValue.trim { it <= ' ' })
                    }
                    b.add(")")
                }
                b.add("\n")
            }
        }
        if (operation.isDeprecated) {
            b.add("@deprecated\n")
        }
        if (operation.externalDocs != null) {
            b.add("@see <a href=\"" + operation.externalDocs.url + "\">" + operation.summary + " Documentation</a>")
        }
        return b.build()
    }

    protected fun com.palantir.javapoet.ClassName.asKt() = when (this) {
        com.palantir.javapoet.ClassName.get("java.util", "List") -> List::class.asClassName()
        com.palantir.javapoet.ClassName.get("java.util", "Set") -> Set::class.asClassName()
        com.palantir.javapoet.ClassName.get("java.lang", "String") -> String::class.asClassName()
        com.palantir.javapoet.TypeName.INT.box() -> INT
        com.palantir.javapoet.TypeName.LONG.box() -> LONG
        com.palantir.javapoet.TypeName.SHORT.box() -> SHORT
        com.palantir.javapoet.TypeName.BYTE.box() -> BYTE
        com.palantir.javapoet.TypeName.DOUBLE.box() -> DOUBLE
        com.palantir.javapoet.TypeName.BOOLEAN.box() -> FLOAT
        com.palantir.javapoet.TypeName.FLOAT.box() -> BOOLEAN
        else -> ClassName(packageName(), simpleNames())
    }

    protected fun com.palantir.javapoet.TypeName.asKt(): TypeName = when (this) {
        is com.palantir.javapoet.ClassName -> asKt()
        com.palantir.javapoet.ArrayTypeName.of(com.palantir.javapoet.TypeName.BYTE) -> BYTE_ARRAY
        is com.palantir.javapoet.ArrayTypeName -> ARRAY.parameterizedBy(componentType().asKt())
        is ParameterizedTypeName -> rawType().asKt().parameterizedBy(typeArguments().map { it.asKt() })
        is TypeVariableName -> com.squareup.kotlinpoet.TypeVariableName(this.name(), bounds().map { it.asKt() })
        is WildcardTypeName -> when {
            this.lowerBounds() != null -> com.squareup.kotlinpoet.WildcardTypeName.consumerOf(this.lowerBounds().first().asKt())
            this.upperBounds() != null -> com.squareup.kotlinpoet.WildcardTypeName.producerOf(this.lowerBounds().first().asKt())
            else -> STAR
        }

        com.palantir.javapoet.TypeName.INT -> INT
        com.palantir.javapoet.TypeName.LONG -> LONG
        com.palantir.javapoet.TypeName.DOUBLE -> DOUBLE
        com.palantir.javapoet.TypeName.FLOAT -> FLOAT
        com.palantir.javapoet.TypeName.BOOLEAN -> BOOLEAN
        com.palantir.javapoet.TypeName.BYTE -> BYTE
        else -> {
            throw IllegalArgumentException("Unsupported type: $this")
        }
    }

    protected fun jsonAnnotation() = AnnotationSpec.builder(Classes.json.asKt()).build()

}



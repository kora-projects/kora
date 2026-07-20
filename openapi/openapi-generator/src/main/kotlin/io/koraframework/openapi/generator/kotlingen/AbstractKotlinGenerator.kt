package io.koraframework.openapi.generator.kotlingen

import com.palantir.javapoet.ArrayTypeName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeVariableName
import com.palantir.javapoet.WildcardTypeName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.openapi.generator.AbstractGenerator
import io.koraframework.openapi.generator.CodegenParams
import io.koraframework.openapi.generator.KoraCodegen
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.IJsonSchemaValidationProperties
import org.openapitools.codegen.model.OperationsMap


abstract class AbstractKotlinGenerator<C : Any> : AbstractGenerator<C, FileSpec>() {
    protected fun buildAdditionalMethodAnnotations(ctx: OperationsMap, operation: CodegenOperation): List<AnnotationSpec> {
        val configPath = if (params.codegenMode.isClient) {
            clientConfigPath(ctx["classname"].toString())
        } else {
            serverConfigPath(ctx["classname"].toString() + "Controller")
        }
        val result = mutableListOf<AnnotationSpec>()
        for (extension in resolveExtensions(ctx, operation)) {
            result.addAnnotations(extension.additionalMethodAnnotations(), configPath)
        }
        return result
    }

    protected fun buildAdditionalModelTypeAnnotations(): List<AnnotationSpec> {
        val result = mutableListOf<AnnotationSpec>()
        params.extensions.global()?.let {
            result.addAnnotations(it.additionalTypeAnnotations(), null)
            result.addAnnotations(it.additionalModelTypeAnnotations(), null)
        }
        return result
    }

    protected fun buildAdditionalEnumTypeAnnotations(): List<AnnotationSpec> {
        val result = mutableListOf<AnnotationSpec>()
        params.extensions.global()?.let {
            result.addAnnotations(it.additionalTypeAnnotations(), null)
            result.addAnnotations(it.additionalEnumTypeAnnotations(), null)
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

    protected fun securityTagAnnotation(securityTagName: String): AnnotationSpec {
        return AnnotationSpec.builder(Classes.tag.asKt())
            .addMember("value = %T.%N::class", ClassName(apiPackage, "ApiSecurity"), securityTagName)
            .build()
    }

    protected fun buildInterceptors(ctx: OperationsMap, operation: CodegenOperation, defaultInterceptorType: ClassName): List<AnnotationSpec> {
        val result = mutableListOf<AnnotationSpec>()
        for (extension in resolveExtensions(ctx, operation)) {
            if (extension.interceptorType() == null && extension.interceptorTag().isEmpty()) {
                continue
            }
            val type = extension.interceptorType()
                ?.let { com.palantir.javapoet.ClassName.bestGuess(it).asKt() }
                ?: defaultInterceptorType
            if (extension.interceptorTag().isEmpty()) {
                result.add(
                    AnnotationSpec.builder(Classes.interceptWith.asKt())
                        .addMember("value = %T::class", type)
                        .build()
                )
            } else {
                for (interceptorTag in extension.interceptorTag()) {
                    result.add(
                        AnnotationSpec.builder(Classes.interceptWith.asKt())
                            .addMember("value = %T::class", type)
                            .addMember("tag = %T::class", com.palantir.javapoet.ClassName.bestGuess(interceptorTag).asKt())
                            .build()
                    )
                }
            }
        }
        return result
    }

    protected fun resolveExtensions(ctx: OperationsMap, operation: CodegenOperation): List<CodegenParams.GeneratorExtension> {
        val result = mutableListOf<CodegenParams.GeneratorExtension>()
        params.extensions.global()?.let(result::add)
        params.extensions.tags()[ctx["baseName"].toString()]?.let(result::add)
        params.extensions.operations()[operation.operationId]?.let(result::add)
        return result
    }

    private fun MutableList<AnnotationSpec>.addAnnotations(annotations: List<String>, configPath: String?) {
        for (annotation in annotations) {
            if (annotation.isNotBlank()) {
                add(parseAnnotation(annotation, configPath))
            }
        }
    }

    private fun parseAnnotation(annotation: String, configPath: String?): AnnotationSpec {
        var value = configPath?.let { annotation.replace("%{configPath}", it) } ?: annotation
        value = value.trim()
        if (value.startsWith("@")) {
            value = value.substring(1)
        }
        val argumentsStart = value.indexOf('(')
        if (argumentsStart < 0) {
            return AnnotationSpec.builder(com.palantir.javapoet.ClassName.bestGuess(value).asKt()).build()
        }
        val type = value.substring(0, argumentsStart).trim()
        val arguments = value.substring(argumentsStart + 1, value.lastIndexOf(')')).trim()
        val builder = AnnotationSpec.builder(com.palantir.javapoet.ClassName.bestGuess(type).asKt())
        if (arguments.isNotBlank()) {
            for (argument in splitAnnotationArguments(arguments)) {
                val eq = argument.indexOf('=')
                if (eq < 0) {
                    builder.addMember("%L", argument.trim())
                } else {
                    builder.addMember("%N = %L", argument.substring(0, eq).trim(), argument.substring(eq + 1).trim())
                }
            }
        }
        return builder.build()
    }

    private fun splitAnnotationArguments(arguments: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var depth = 0
        var inString = false
        for (i in arguments.indices) {
            val c = arguments[i]
            if (c == '"' && (i == 0 || arguments[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString && (c == '(' || c == '{' || c == '[')) {
                depth++
            } else if (!inString && (c == ')' || c == '}' || c == ']')) {
                depth--
            } else if (!inString && depth == 0 && c == ',') {
                result.add(arguments.substring(start, i))
                start = i + 1
            }
        }
        result.add(arguments.substring(start))
        return result
    }

    private fun clientConfigPath(clientName: String): String? {
        params.clientConfigPrefix?.takeIf { it.isNotBlank() }?.let {
            return it + "." + StringUtils.uncapitalize(clientName)
        }
        return params.clientConfig
    }

    private fun serverConfigPath(controllerTypeName: String): String {
        return params.serverConfigPrefix.replace("%{ControllerTypeNameInCamelCase}", StringUtils.uncapitalize(controllerTypeName))
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

            param.isBodyParam && KoraCodegen.isContentJson(param) && !isBareObject(param) -> b.addAnnotation(
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
        com.palantir.javapoet.ClassName.get("java.util", "Map") -> Map::class.asClassName()
        com.palantir.javapoet.ClassName.get("java.util", "Set") -> Set::class.asClassName()
        com.palantir.javapoet.ClassName.get("java.lang", "String") -> String::class.asClassName()
        com.palantir.javapoet.ClassName.get("java.lang", "Object") -> ANY
        com.palantir.javapoet.ClassName.get("java.math", "BigDecimal") -> java.math.BigDecimal::class.asClassName()
        com.palantir.javapoet.ClassName.get("io.koraframework.http.common.body", "HttpBodyInput") -> ClassName("io.koraframework.http.common.body", "HttpBodyInput")
        com.palantir.javapoet.ClassName.get("io.koraframework.http.common.body", "HttpBodyOutput") -> ClassName("io.koraframework.http.common.body", "HttpBodyOutput")
        com.palantir.javapoet.TypeName.INT.box() -> INT
        com.palantir.javapoet.TypeName.LONG.box() -> LONG
        com.palantir.javapoet.TypeName.SHORT.box() -> SHORT
        com.palantir.javapoet.TypeName.BYTE.box() -> BYTE
        com.palantir.javapoet.TypeName.DOUBLE.box() -> DOUBLE
        com.palantir.javapoet.TypeName.BOOLEAN.box() -> BOOLEAN
        com.palantir.javapoet.TypeName.FLOAT.box() -> FLOAT
        else -> ClassName(packageName(), simpleNames())
    }

    protected fun com.palantir.javapoet.TypeName.asKt(): TypeName = when (this) {
        is com.palantir.javapoet.ClassName -> asKt()
        ArrayTypeName.of(com.palantir.javapoet.TypeName.BYTE) -> BYTE_ARRAY
        is ArrayTypeName -> ARRAY.parameterizedBy(componentType().asKt())
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

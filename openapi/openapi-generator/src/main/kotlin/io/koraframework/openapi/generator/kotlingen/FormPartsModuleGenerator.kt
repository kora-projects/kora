package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.openapi.generator.javagen.FormPartsModuleGenerator.CLASS_NAME
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.TreeMap

/**
 * Default converters of form parts that declare a non-JSON `encoding.contentType`: a mapper asks for an untagged converter,
 * and the default one delegates to the `@Json` tagged converter until an own component replaces it
 */
class FormPartsModuleGenerator : AbstractKotlinGenerator<Map<String, Any>>() {

    override fun generate(ctx: Map<String, Any>): FileSpec {
        val className = ClassName(apiPackage, CLASS_NAME)
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder("log", Logger::class.asClassName())
                            .initializer("%T.getLogger(%T::class.java)", LoggerFactory::class.asClassName(), className)
                            .build()
                    )
                    .build()
            )

        val isClient = params.codegenMode.isClient
        val converter = (if (isClient) Classes.stringParameterConverter else Classes.stringParameterReader).asKt()
        val suffix = if (isClient) "FormPartWriter" else "FormPartReader"
        val functionNames = HashSet<String>()
        for ((valueType, usages) in fallbackParts()) {
            val converterType = converter.parameterizedBy(valueType)
            var functionName = functionName(valueType, suffix)
            var i = 2
            while (!functionNames.add(functionName)) {
                functionName = functionName(valueType, suffix + i++)
            }
            val delegate = if (isClient) "jsonWriter" else "jsonReader"
            b.addFunction(
                FunSpec.builder(functionName)
                    .addAnnotation(Classes.defaultComponent.asKt())
                    .returns(converterType)
                    .addParameter(ParameterSpec.builder(delegate, converterType).addAnnotation(Classes.json.asKt()).build())
                    .addStatement("log.info(%S)", logMessage(converterType, isClient, usages))
                    .addStatement("return·%T·{·value·->·%N.%N(value)·}", converterType, delegate, if (isClient) "convert" else "read")
                    .build()
            )
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun fallbackParts(): Map<TypeName, MutableList<String>> {
        val parts = LinkedHashMap<TypeName, MutableList<String>>()
        for (operations in TreeMap(operationsByClassName).values) {
            for (operation in operations.operations.operation) {
                for (p in operation.formParams) {
                    if (!isJsonFallbackFormPart(p)) {
                        continue
                    }
                    val paramType = asType(p).asKt()
                    // an array part is converted element by element
                    val valueType = (if (p.isArray) (paramType as ParameterizedTypeName).typeArguments.single() else paramType)
                        .copy(nullable = false, annotations = emptyList())
                    parts.computeIfAbsent(valueType) { ArrayList() }.add("${operation.operationId}.${p.baseName} (${p.contentType})")
                }
            }
        }
        return parts
    }

    private fun logMessage(converterType: TypeName, isClient: Boolean, usages: List<String>): String {
        val (done, todo) = if (isClient) "written" to "write" else "read" to "read"
        return "OpenAPI form parts with non-JSON `encoding.contentType` are $done as JSON: ${usages.joinToString(", ")}. " +
            "Provide own $converterType component to $todo them in the declared format."
    }

    private fun functionName(valueType: TypeName, suffix: String): String =
        (simpleName(valueType) + suffix).replaceFirstChar { it.lowercaseChar() }

    private fun simpleName(type: TypeName): String = when (type) {
        is ClassName -> type.simpleNames.joinToString("")
        is ParameterizedTypeName -> simpleName(type.rawType) + type.typeArguments.joinToString("") { simpleName(it) }
        else -> type.copy(nullable = false, annotations = emptyList()).toString().replace(Regex("\\W"), "")
    }
}

package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.openapi.generator.javagen.FormPartsModuleGenerator.CLASS_NAME
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.TreeMap
import java.util.TreeSet

/**
 * Tags of form part converters, one per media type declared in `encoding.contentType` besides plain JSON.
 * A converter of a JSON-like type (`+json`) has a default component that delegates to the `@Json` tagged converter,
 * a converter of any other type is provided by an application
 */
class FormPartsModuleGenerator : AbstractKotlinGenerator<Map<String, Any>>() {

    private data class DefaultConverter(val tag: String, val valueType: TypeName)

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

        val tags = TreeSet<String>()
        val defaultConverters = LinkedHashMap<DefaultConverter, MutableList<String>>()
        for (operations in TreeMap(operationsByClassName).values) {
            for (operation in operations.operations.operation) {
                for (p in operation.formParams) {
                    val tag = formPartTag(p) ?: continue
                    tags.add(tag)
                    if (hasDefaultFormPartConverter(p)) {
                        val paramType = asType(p).asKt()
                        // an array part is converted element by element
                        val valueType = (if (p.isArray) (paramType as ParameterizedTypeName).typeArguments.single() else paramType)
                            .copy(nullable = false, annotations = emptyList())
                        defaultConverters.computeIfAbsent(DefaultConverter(tag, valueType)) { ArrayList() }
                            .add("${operation.operationId}.${p.baseName} (${formPartContentType(p)})")
                    }
                }
            }
        }
        for (tag in tags) {
            b.addType(TypeSpec.classBuilder(tag).addAnnotation(generated()).build())
        }

        val isClient = params.codegenMode.isClient
        val converter = (if (isClient) Classes.stringParameterConverter else Classes.stringParameterReader).asKt()
        val delegate = if (isClient) "jsonWriter" else "jsonReader"
        for ((key, usages) in defaultConverters) {
            val converterType = converter.parameterizedBy(key.valueType)
            val functionName = simpleName(key.valueType) + key.tag + (if (isClient) "FormPartWriter" else "FormPartReader")
            b.addFunction(
                FunSpec.builder(functionName.replaceFirstChar { it.lowercaseChar() })
                    .addAnnotation(formPartTagAnnotation(key.tag))
                    .addAnnotation(Classes.defaultComponent.asKt())
                    .returns(converterType)
                    .addParameter(ParameterSpec.builder(delegate, converterType).addAnnotation(Classes.json.asKt()).build())
                    .addStatement("log.info(%S)", logMessage(key.tag, converterType, isClient, usages))
                    .addStatement("return·%T·{·value·->·%N.%N(value)·}", converterType, delegate, if (isClient) "convert" else "read")
                    .build()
            )
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun logMessage(tag: String, converterType: TypeName, isClient: Boolean, usages: List<String>): String {
        val done = if (isClient) "written" else "read"
        return "OpenAPI form parts of a JSON-like media type are $done with the @Json converter: ${usages.joinToString(", ")}. " +
            "Provide own @Tag($CLASS_NAME.$tag::class) $converterType component to override it."
    }

    private fun simpleName(type: TypeName): String = when (type) {
        is ClassName -> type.simpleNames.joinToString("")
        is ParameterizedTypeName -> simpleName(type.rawType) + type.typeArguments.joinToString("") { simpleName(it) }
        else -> type.copy(nullable = false, annotations = emptyList()).toString().replace(Regex("\\W"), "")
    }
}

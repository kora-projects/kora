package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

/**
 * Generates a delegating [ru.tinkoff.kora.json.common.JsonWriter] for a non-enum type that declares a
 * `@JsonWriter`-annotated method producing the single JSON value the type is serialized as
 * (Jackson `@JsonValue` semantics). The method is either an instance method `() -> V` or a static
 * method (Kotlin: companion object) `(T) -> V`.
 */
class DelegatingJsonWriterGenerator {
    data class WriterValue(val valueType: TypeName, val accessor: String, val isStatic: Boolean)

    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val className = declaration.toClassName()
        val typeName = declaration.toTypeName()
        val value = detectWriterMethod(declaration)
            ?: throw ProcessingErrorException("No @JsonWriter method found on ${declaration.simpleName.asString()}", declaration)
        val valueWriterType = JsonTypes.jsonWriter.parameterizedBy(value.valueType)

        val extracted = if (value.isStatic) {
            CodeBlock.of("%T.%N(_object)", className, value.accessor)
        } else {
            CodeBlock.of("_object.%N()", value.accessor)
        }
        val body = CodeBlock.builder()
            .beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()")
            .addStatement("return")
            .endControlFlow()
            .addStatement("this.valueWriter.write(_gen, %L)", extracted)
            .build()

        return TypeSpec.classBuilder(declaration.jsonWriterName())
            .generated(DelegatingJsonWriterGenerator::class)
            .addSuperinterface(JsonTypes.jsonWriter.parameterizedBy(typeName))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("valueWriter", valueWriterType)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("valueWriter", valueWriterType, KModifier.PRIVATE)
                    .initializer("valueWriter")
                    .build()
            )
            .addFunction(
                FunSpec.builder("write")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addParameter("_gen", JsonTypes.jsonGenerator)
                    .addParameter("_object", typeName.copy(nullable = true))
                    .addCode(body)
                    .build()
            )
            .addOriginatingKSFile(declaration)
            .build()
    }

    fun detectWriterMethod(declaration: KSClassDeclaration): WriterValue? {
        val companion = declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
        val companionMethods = companion
            ?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            ?.toList()
            .orEmpty()
        val bodyMethods = declaration.getAllFunctions()
            .filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            .toList()
        val methods = companionMethods + bodyMethods
        if (methods.isEmpty()) {
            return null
        }
        if (methods.size > 1) {
            throw ProcessingErrorException(
                "Type ${declaration.simpleName.asString()} has multiple @JsonWriter methods, only one is allowed",
                methods[1]
            )
        }
        val method = methods[0]
        if (!method.isPublic()) {
            throw ProcessingErrorException("@JsonWriter method must be public", method)
        }
        val returnDeclaration = method.returnType?.resolve()?.declaration
        if (returnDeclaration == null || returnDeclaration.qualifiedName?.asString() == "kotlin.Unit") {
            throw ProcessingErrorException("@JsonWriter method must return a value", method)
        }
        val valueType = method.returnType!!.toTypeName()
        val isStatic = method in companionMethods
        if (isStatic) {
            if (method.parameters.size != 1) {
                throw ProcessingErrorException(
                    "@JsonWriter static method must have exactly one parameter (the value type), got ${method.parameters.size}",
                    method
                )
            }
            val paramDeclaration = method.parameters[0].type.resolve().declaration
            if (paramDeclaration != declaration) {
                throw ProcessingErrorException(
                    "@JsonWriter static method parameter must be of type ${declaration.simpleName.asString()}",
                    method
                )
            }
        } else {
            if (method.parameters.isNotEmpty()) {
                throw ProcessingErrorException(
                    "@JsonWriter instance method must have no parameters, got ${method.parameters.size}",
                    method
                )
            }
        }
        return WriterValue(valueType, method.simpleName.asString(), isStatic)
    }
}

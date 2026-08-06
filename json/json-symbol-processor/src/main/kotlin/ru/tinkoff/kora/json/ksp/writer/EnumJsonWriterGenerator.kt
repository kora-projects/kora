package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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

class EnumJsonWriterGenerator {
    fun generateEnumWriter(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()
        val enumType = detectValueType(jsonClassDeclaration)

        val extractor = if (enumType.isStatic) {
            CodeBlock.of("{ e: %T -> %T.%N(e) }", className, className, enumType.accessor)
        } else {
            CodeBlock.of("%T::%N", className, enumType.accessor)
        }

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("valueWriter", JsonTypes.jsonWriter.parameterizedBy(enumType.type))
                    .build()
            )
            .addSuperinterface(
                JsonTypes.jsonWriter.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), %L, valueWriter)", JsonTypes.enumJsonWriter, className, extractor)
            )
            .addOriginatingKSFile(jsonClassDeclaration)
        return typeBuilder.build()
    }


    data class EnumValue(val type: TypeName, val accessor: String, val isStatic: Boolean = false)

    fun detectValueType(typeElement: KSClassDeclaration): EnumValue {
        detectWriterMethod(typeElement)?.let { return it }
        for (function in typeElement.getAllFunctions()) {
            if (function.isPublic() && function.parameters.isEmpty() && function.isAnnotationPresent(JsonTypes.json)) {
                val typeName = function.returnType!!.toTypeName()
                return EnumValue(typeName, function.simpleName.asString())
            }
        }
        val typeName = String::class.asTypeName()
        return EnumValue(typeName, "toString")
    }

    fun detectWriterMethod(enumDeclaration: KSClassDeclaration): EnumValue? {
        val companion = enumDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
        val companionMethods = companion
            ?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            ?.toList()
            .orEmpty()
        val bodyMethods = enumDeclaration.getAllFunctions()
            .filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            .toList()
        // A property annotated at its getter (@get:JsonWriter val value) is also a valid instance value source:
        // @JsonWriter targets METHOD, so on a Kotlin property it can only sit on the getter accessor.
        val getterProperties = enumDeclaration.getDeclaredProperties()
            .filter { it.getter?.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) == true }
            .toList()
        val targets: List<KSDeclaration> = companionMethods + bodyMethods + getterProperties
        if (targets.isEmpty()) {
            return null
        }
        if (targets.size > 1) {
            throw ProcessingErrorException(
                "Enum ${enumDeclaration.simpleName.asString()} has multiple @JsonWriter members, only one is allowed",
                targets[1]
            )
        }
        val property = getterProperties.firstOrNull()
        if (property != null) {
            if (!property.isPublic()) {
                throw ProcessingErrorException("@JsonWriter property must be public", property)
            }
            if (property.type.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit") {
                throw ProcessingErrorException("@JsonWriter property must have a value type", property)
            }
            // Referenced in generated code as Enum::property, i.e. a KProperty1<Enum, V> that acts as (Enum) -> V.
            return EnumValue(property.type.toTypeName(), property.simpleName.asString(), isStatic = false)
        }
        // A companion-object (static) method receives the enum as its single argument; an instance method
        // already has the enum as its receiver and therefore must take none. The parameter count disambiguates
        // the two forms, so both are honoured and only their shape is validated.
        val method = targets[0] as KSFunctionDeclaration
        if (!method.isPublic()) {
            throw ProcessingErrorException("@JsonWriter method must be public", method)
        }
        val returnDeclaration = method.returnType?.resolve()?.declaration
        if (returnDeclaration == null || returnDeclaration.qualifiedName?.asString() == "kotlin.Unit") {
            throw ProcessingErrorException("@JsonWriter method must return a value", method)
        }
        val isStatic = method in companionMethods
        if (isStatic) {
            if (method.parameters.size != 1) {
                throw ProcessingErrorException(
                    "@JsonWriter static (companion object) method must have exactly one parameter of type ${enumDeclaration.simpleName.asString()}, got ${method.parameters.size}",
                    method
                )
            }
            val paramDeclaration = method.parameters[0].type.resolve().declaration
            if (paramDeclaration != enumDeclaration) {
                throw ProcessingErrorException(
                    "@JsonWriter static (companion object) method parameter must be of type ${enumDeclaration.simpleName.asString()}",
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
        return EnumValue(method.returnType!!.toTypeName(), method.simpleName.asString(), isStatic = isStatic)
    }
}

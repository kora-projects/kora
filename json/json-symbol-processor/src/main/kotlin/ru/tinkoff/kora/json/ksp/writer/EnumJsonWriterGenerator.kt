package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class EnumJsonWriterGenerator {
    fun generateEnumWriter(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()
        val enumType = detectValueType(jsonClassDeclaration)

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("valueWriter", JsonTypes.jsonWriter.parameterizedBy(enumType.type))
                .build()
            )
            .addSuperinterface(
                JsonTypes.jsonWriter.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), %T::%N, valueWriter)", JsonTypes.enumJsonWriter, className, className, enumType.accessor)
            )
        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        return typeBuilder.build()
    }


    data class EnumValue(val type: TypeName, val accessor: String)

    fun detectValueType(typeElement: KSClassDeclaration): EnumValue {
        for (function in typeElement.getAllFunctions()) {
            if (function.isPublic() && function.parameters.isEmpty() && function.isAnnotationPresent(JsonTypes.json)) {
                val typeName = function.returnType!!.toTypeName()
                return EnumValue(typeName, function.simpleName.asString())
            }
        }
        val typeName = String::class.asTypeName()
        return EnumValue(typeName, "toString")
    }
}

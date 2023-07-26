package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class EnumJsonReaderGenerator {
    fun generateEnumReader(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()
        val enumType = detectValueType(jsonClassDeclaration)

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonReaderName())
            .generated(JsonReaderGenerator::class)
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("valueReader", JsonTypes.jsonReader.parameterizedBy(enumType.type))
                .build()
            )
            .addSuperinterface(
                JsonTypes.jsonReader.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), %T::%N, valueReader)", JsonTypes.enumJsonReader, className, className, enumType.accessor)
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

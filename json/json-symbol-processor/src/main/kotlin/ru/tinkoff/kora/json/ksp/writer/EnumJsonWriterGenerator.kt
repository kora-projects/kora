package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class EnumJsonWriterGenerator {
    fun generateEnumWriter(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()
        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .addSuperinterface(
                JsonTypes.jsonWriter.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), { it.toString() })", JsonTypes.enumJsonWriter, className)
            ) // todo detect to string method
        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        return typeBuilder.build()
    }
}

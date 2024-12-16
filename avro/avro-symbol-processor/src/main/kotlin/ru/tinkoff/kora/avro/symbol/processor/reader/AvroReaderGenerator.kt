package ru.tinkoff.kora.avro.symbol.processor.reader

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.throws
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.avro.symbol.processor.AvroTypes
import ru.tinkoff.kora.avro.symbol.processor.classPackage
import ru.tinkoff.kora.avro.symbol.processor.readerBinaryName
import ru.tinkoff.kora.avro.symbol.processor.readerJsonName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import java.io.IOException
import java.io.InputStream

class AvroReaderGenerator(val resolver: Resolver, private val codeGenerator: CodeGenerator) {

    fun generateBinary(declaration: KSClassDeclaration) {
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val readerInterface = AvroTypes.reader.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.readerBinaryName())
            .generated(AvroReaderGenerator::class)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        typeBuilder.addSuperinterface(readerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        typeBuilder.addProperty(
            PropertySpec.builder("SCHEMA", AvroTypes.schema)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T.getClassSchema()", typeName)
                .build()
        )
        typeBuilder.addProperty(
            PropertySpec.builder("SPECIFIC_DATA", AvroTypes.specificData)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T().getSpecificData()", typeName)
                .build()
        )
        typeBuilder.addProperty(
            PropertySpec.builder("READER", AvroTypes.datumReader.parameterizedBy(typeName))
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(SCHEMA, SCHEMA, SPECIFIC_DATA)", AvroTypes.datumReader)
                .build()
        )

        val method = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .throws(IOException::class)
            .addParameter("value", InputStream::class.asTypeName().copy(true))
            .returns(typeName.copy(true))
        method.beginControlFlow("if (value == null || value.available() == 0)")
        method.addStatement("return null")
        method.endControlFlow()
        method.addStatement("val decoder = %T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory)
        method.addStatement("return READER.read(%T(), decoder)", typeName)

        typeBuilder.addFunction(method.build())
        val spec = typeBuilder.build()

        val packageElement = classPackage(declaration)
        val fileSpec = FileSpec.builder(
            packageName = packageElement,
            fileName = spec.name!!
        )
        fileSpec.addType(spec)
        fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    fun generateJson(declaration: KSClassDeclaration) {
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val readerInterface = AvroTypes.reader.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.readerJsonName())
            .generated(AvroReaderGenerator::class)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        typeBuilder.addSuperinterface(readerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        typeBuilder.addProperty(
            PropertySpec.builder("SCHEMA", AvroTypes.schema)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T.getClassSchema()", typeName)
                .build()
        )
        typeBuilder.addProperty(
            PropertySpec.builder("SPECIFIC_DATA", AvroTypes.specificData)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T().getSpecificData()", typeName)
                .build()
        )
        typeBuilder.addProperty(
            PropertySpec.builder("READER", AvroTypes.datumReader.parameterizedBy(typeName))
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(SCHEMA, SCHEMA, SPECIFIC_DATA)", AvroTypes.datumReader)
                .build()
        )

        val method = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .throws(IOException::class)
            .addParameter("value", InputStream::class.asTypeName().copy(true))
            .returns(typeName.copy(true))
        method.beginControlFlow("if (value == null || value.available() == 0)")
        method.addStatement("return null")
        method.endControlFlow()
        method.addStatement("val decoder = %T.get().jsonDecoder(SCHEMA, value)", AvroTypes.decoderFactory)
        method.addStatement("return READER.read(%T(), decoder)", typeName)

        typeBuilder.addFunction(method.build())
        val spec = typeBuilder.build()

        val packageElement = classPackage(declaration)
        val fileSpec = FileSpec.builder(
            packageName = packageElement,
            fileName = spec.name!!
        )
        fileSpec.addType(spec)
        fileSpec.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}

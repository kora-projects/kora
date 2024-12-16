package ru.tinkoff.kora.avro.symbol.processor.writer

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
import ru.tinkoff.kora.avro.symbol.processor.writerJsonName
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import java.io.ByteArrayOutputStream
import java.io.IOException

class AvroWriterGenerator(val resolver: Resolver, val codeGenerator: CodeGenerator) {

    fun generateBinary(declaration: KSClassDeclaration) {
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val writerInterface = AvroTypes.writer.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.writerJsonName())
            .generated(AvroWriterGenerator::class)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        typeBuilder.addSuperinterface(writerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        typeBuilder.addProperty(
            PropertySpec.builder("EMPTY", ByteArray::class)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(0)", ByteArray::class)
                .build()
        )
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
            PropertySpec.builder("WRITER", AvroTypes.datumWriter.parameterizedBy(typeName))
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(SCHEMA, SPECIFIC_DATA)", AvroTypes.datumWriter)
                .build()
        )

        val method = FunSpec.builder("writeBytes")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .throws(IOException::class)
            .addParameter("value", typeName.copy(true))
            .returns(ByteArray::class)
        method.beginControlFlow("if (value == null)")
        method.addStatement("return EMPTY")
        method.endControlFlow()
        method.beginControlFlow("return %T().%M", ByteArrayOutputStream::class, MemberName("kotlin.io", "use"))
        method.addStatement("val encoder = %T.get().directBinaryEncoder(it, null)", AvroTypes.encoderFactory)
        method.addStatement("WRITER.write(value, encoder)", typeName)
        method.addStatement("encoder.flush()")
        method.addStatement("it.toByteArray()")
        method.endControlFlow()

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
        val writerInterface = AvroTypes.writer.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.writerJsonName())
            .generated(AvroWriterGenerator::class)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        typeBuilder.addSuperinterface(writerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        typeBuilder.addProperty(
            PropertySpec.builder("EMPTY", ByteArray::class)
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(0)", ByteArray::class)
                .build()
        )
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
            PropertySpec.builder("WRITER", AvroTypes.datumWriter.parameterizedBy(typeName))
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T(SCHEMA, SPECIFIC_DATA)", AvroTypes.datumWriter)
                .build()
        )

        val method = FunSpec.builder("writeBytes")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .throws(IOException::class)
            .addParameter("value", typeName.copy(true))
            .returns(ByteArray::class)
        method.beginControlFlow("if (value == null)")
        method.addStatement("return EMPTY")
        method.endControlFlow()
        method.beginControlFlow("return %T().%M", ByteArrayOutputStream::class, MemberName("kotlin.io", "use"))
        method.addStatement("val encoder = %T.get().jsonEncoder(SCHEMA, it)", AvroTypes.encoderFactory)
        method.addStatement("WRITER.write(value, encoder)", typeName)
        method.addStatement("encoder.flush()")
        method.addStatement("it.toByteArray()")
        method.endControlFlow()

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

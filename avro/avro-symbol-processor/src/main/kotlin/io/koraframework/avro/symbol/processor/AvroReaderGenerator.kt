package io.koraframework.avro.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException

class AvroReaderGenerator(val resolver: Resolver, private val codeGenerator: CodeGenerator) {
    fun generate(declaration: KSClassDeclaration) {
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val readerInterface = AvroTypes.reader.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.readerName())
            .generated(AvroReaderGenerator::class)
            .addAnnotation(AvroTypes.avro)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        typeBuilder.addSuperinterface(readerInterface)
        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }
        typeBuilder.addProperty(PropertySpec.builder("SCHEMA", AvroTypes.schema).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T.getClassSchema()", typeName).build())
        typeBuilder.addProperty(PropertySpec.builder("SPECIFIC_DATA", AvroTypes.specificData).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T().getSpecificData()", typeName).build())
        typeBuilder.addProperty(PropertySpec.builder("READER", AvroTypes.datumReader.parameterizedBy(typeName)).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T(SCHEMA, SCHEMA, SPECIFIC_DATA)", AvroTypes.datumReader).build())
        typeBuilder.addProperty(
            PropertySpec.builder("READERS_BY_WRITER_SCHEMA", AvroTypes.concurrentMap.parameterizedBy(AvroTypes.schema, AvroTypes.datumReader.parameterizedBy(typeName)))
                .addModifiers(KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T()", AvroTypes.concurrentHashMap)
                .build()
        )

        typeBuilder.addFunction(
            FunSpec.builder("getSchema")
                .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
                .returns(AvroTypes.schema)
                .addStatement("return SCHEMA")
                .build()
        )

        val method = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .addParameter("value", InputStream::class.asTypeName().copy(true))
            .returns(typeName.copy(true))
        method.beginControlFlow("try")
        method.beginControlFlow("if (value == null || value.available() == 0)")
        method.addStatement("return null")
        method.endControlFlow()
        method.addStatement("val decoder = %T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory)
        method.addStatement("return READER.read(%T(), decoder)", typeName)
        method.nextControlFlow("catch (e: %T)", IOException::class)
        method.addStatement("throw %T(e)", UncheckedIOException::class)
        method.endControlFlow()
        typeBuilder.addFunction(method.build())

        val resolvingMethod = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL, KModifier.OVERRIDE)
            .addParameter("writerSchema", AvroTypes.schema.copy(true))
            .addParameter("value", InputStream::class.asTypeName().copy(true))
            .returns(typeName.copy(true))
        resolvingMethod.beginControlFlow("try")
        resolvingMethod.beginControlFlow("if (value == null || value.available() == 0)")
        resolvingMethod.addStatement("return null")
        resolvingMethod.endControlFlow()
        resolvingMethod.beginControlFlow("if (writerSchema == null || SCHEMA == writerSchema)")
        resolvingMethod.addStatement("return read(value)")
        resolvingMethod.endControlFlow()
        resolvingMethod.addStatement("val reader = READERS_BY_WRITER_SCHEMA.computeIfAbsent(writerSchema) { ws -> %T(ws, SCHEMA, SPECIFIC_DATA) }", AvroTypes.datumReader)
        resolvingMethod.addStatement("val decoder = %T.get().directBinaryDecoder(value, null)", AvroTypes.decoderFactory)
        resolvingMethod.addStatement("return reader.read(%T(), decoder)", typeName)
        resolvingMethod.nextControlFlow("catch (e: %T)", IOException::class)
        resolvingMethod.addStatement("throw %T(e)", UncheckedIOException::class)
        resolvingMethod.endControlFlow()
        typeBuilder.addFunction(resolvingMethod.build())

        val spec = typeBuilder.build()
        FileSpec.builder(classPackage(declaration), spec.name!!).addType(spec).build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}

package io.koraframework.avro.symbol.processor.writer

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
import io.koraframework.avro.symbol.processor.AvroTypes
import io.koraframework.avro.symbol.processor.classPackage
import io.koraframework.avro.symbol.processor.writerName
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import java.io.ByteArrayOutputStream
import java.io.IOException

class AvroWriterGenerator(val resolver: Resolver, val codeGenerator: CodeGenerator) {
    fun generate(declaration: KSClassDeclaration) {
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val writerInterface = AvroTypes.writer.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.writerName())
            .generated(AvroWriterGenerator::class)
            .addAnnotation(AvroTypes.avro)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        typeBuilder.addSuperinterface(writerInterface)
        declaration.typeParameters.forEach { typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver)) }

        typeBuilder.addProperty(PropertySpec.builder("EMPTY", ByteArray::class).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T(0)", ByteArray::class).build())
        typeBuilder.addProperty(PropertySpec.builder("SCHEMA", AvroTypes.schema).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T.getClassSchema()", typeName).build())
        typeBuilder.addProperty(PropertySpec.builder("SPECIFIC_DATA", AvroTypes.specificData).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T().getSpecificData()", typeName).build())
        typeBuilder.addProperty(PropertySpec.builder("WRITER", AvroTypes.datumWriter.parameterizedBy(typeName)).addModifiers(KModifier.PRIVATE, KModifier.FINAL).initializer("%T(SCHEMA, SPECIFIC_DATA)", AvroTypes.datumWriter).build())

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
        FileSpec.builder(classPackage(declaration), spec.name!!).addType(spec).build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }
}

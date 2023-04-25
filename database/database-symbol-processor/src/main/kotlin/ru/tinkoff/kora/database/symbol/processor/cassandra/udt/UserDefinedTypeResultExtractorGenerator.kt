package ru.tinkoff.kora.database.symbol.processor.cassandra.udt

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraNativeTypes
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.generatedClassName

class UserDefinedTypeResultExtractorGenerator(private val environment: SymbolProcessorEnvironment) {
    fun generate(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        this.generateRowColumnMapper(resolver, classDeclaration)
        this.generateListRowColumnMapper(resolver, classDeclaration)
    }

    private fun generateRowColumnMapper(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        val type = classDeclaration.asType(listOf())
        val typeName = type.toTypeName().copy(false)
        val typeSpec = TypeSpec.classBuilder(classDeclaration.generatedClassName("CassandraRowColumnMapper"))
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(CassandraTypes.rowColumnMapper.parameterizedBy(typeName))
        classDeclaration.containingFile?.let { typeSpec.addOriginatingKSFile(it) }
        val constructor = FunSpec.constructorBuilder()
        val entity = DbEntity.parseEntity(type)!!
        this.addMappers(typeSpec, constructor, entity)

        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_row", CassandraTypes.gettableByName)
            .addParameter("_index", Int::class.javaPrimitiveType!!.asTypeName())
            .returns(typeName.copy(true))
        apply.addStatement("val _type = _row.getType(_index) as %T", CassandraTypes.userDefinedType)
        apply.addStatement("val _object = _row.getUdtValue(_index)")
        apply.controlFlow("if (_object == null)") {
            addStatement("return null")
        }
        apply.addCode("\n")
        this.readIndexes(apply, entity)
        this.readFields(apply, entity)
        apply.addCode("return %L\n", buildEntity(entity))
        typeSpec.addFunction(apply.build())
        typeSpec.primaryConstructor(constructor.build())

        FileSpec.get(classDeclaration.packageName.asString(), typeSpec.build()).writeTo(environment.codeGenerator, false, listOfNotNull(classDeclaration.containingFile))
    }

    private fun generateListRowColumnMapper(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        val type = classDeclaration.asType(listOf())
        val typeName = type.toTypeName().copy(false)
        val listTypeName = LIST.parameterizedBy(typeName).copy(false)
        val typeSpec = TypeSpec.classBuilder(classDeclaration.generatedClassName("List_CassandraRowColumnMapper"))
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(CassandraTypes.rowColumnMapper.parameterizedBy(listTypeName))
        classDeclaration.containingFile?.let { typeSpec.addOriginatingKSFile(it) }
        val constructor = FunSpec.constructorBuilder()
        val entity = DbEntity.parseEntity(type)!!
        this.addMappers(typeSpec, constructor, entity)

        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_row", CassandraTypes.gettableByName)
            .addParameter("_index", Int::class.javaPrimitiveType!!.asTypeName())
            .returns(listTypeName.copy(true))
        apply.addStatement("val _listType = _row.getType(_index) as %T", CassandraTypes.listType)
        apply.addStatement("val _type = _listType.getElementType() as %T", CassandraTypes.userDefinedType)
        apply.addStatement("val _list = _row.getList(_index, %T::class.java)", CassandraTypes.udtValue)
        apply.controlFlow("if (_list == null)") {
            addStatement("return null")
        }
        readIndexes(apply, entity)
        apply.addCode("\n")
        apply.controlFlow("return _list.map { _object ->") {
            readFields(this, entity)
            apply.addCode("%L\n", buildEntity(entity))
        }

        typeSpec.addFunction(apply.build())
        typeSpec.primaryConstructor(constructor.build())

        FileSpec.get(classDeclaration.packageName.asString(), typeSpec.build()).writeTo(environment.codeGenerator, false, listOfNotNull(classDeclaration.containingFile))
    }

    private fun readIndexes(apply: FunSpec.Builder, entity: DbEntity) {
        for (entityField in entity.columns) {
            val fieldName = entityField.property.simpleName.asString()
            apply.addStatement("val %N = _type.firstIndexOf(%S)", "_index_of_$fieldName", entityField.columnName)
        }
        apply.addCode("\n")
    }

    private fun readFields(apply: FunSpec.Builder, entity: DbEntity) {
        for (entityField in entity.columns) {
            val fieldName = entityField.property.simpleName.asString()
            val fieldTypeName = entityField.type.toTypeName()
            val nativeType = CassandraNativeTypes.findNativeType(fieldTypeName)
            apply.controlFlow("val %N = if (_object.isNull(%N))", fieldName, "_index_of_$fieldName") {
                if (fieldTypeName.isNullable) {
                    addStatement("null")
                } else {
                    addStatement("throw %T(%S)", NullPointerException::class, "Field $fieldName is not nullable, but column ${entityField.columnName} is null")
                }
                nextControlFlow("else")
                if (nativeType != null) {
                    addCode(nativeType.extract("_object", CodeBlock.of("%N", "_index_of_$fieldName")))
                } else {
                    val mapperName = "_${fieldName}_mapper"
                    addCode("this.%N.apply(_object, %N)", mapperName, "_index_of_$fieldName")
                }
                addCode("!!\n")
            }
        }
    }

    private fun addMappers(typeSpec: TypeSpec.Builder, constructor: FunSpec.Builder, entity: DbEntity) {
        for (entityField in entity.fields) {
            val fieldName = entityField.property.simpleName.asString()
            val fieldTypeName = entityField.type.toTypeName()
            val nativeType = CassandraNativeTypes.findNativeType(fieldTypeName)
            if (nativeType != null) {
                continue
            }
            val mapperName = "_${fieldName}_mapper"
            val mapperType = CassandraTypes.rowColumnMapper.parameterizedBy(fieldTypeName.copy(false))
            constructor.addParameter(mapperName, mapperType)
            constructor.addStatement("this.%N = %N", mapperName, mapperName)

            typeSpec.addProperty(mapperName, mapperType, KModifier.PRIVATE, KModifier.FINAL)
        }
    }

    private fun buildEntity(entity: DbEntity): CodeBlock {
        val b = CodeBlock.builder()
        b.add("%T(\n", entity.type.toTypeName())
        for (i in 0 until entity.fields.size) {
            if (i > 0) {
                b.add(",\n")
            }
            val field = entity.fields[i]
            val fieldName = field.property.simpleName.asString()
            b.add("  %N", fieldName)
        }
        b.add("\n)")
        return b.build()
    }
}

package ru.tinkoff.kora.database.symbol.processor.cassandra.udt

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraNativeTypes
import ru.tinkoff.kora.database.symbol.processor.cassandra.CassandraTypes
import ru.tinkoff.kora.database.symbol.processor.model.DbEntity
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.generatedClassName

class UserDefinedTypeStatementSetterGenerator(private val environment: SymbolProcessorEnvironment) {

    fun generate(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        this.generateSetter(resolver, classDeclaration)
        this.generateListSetter(resolver, classDeclaration)
    }

    private fun generateSetter(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        val type = classDeclaration.asType(listOf())
        val typeName = type.toTypeName().copy(false)
        val entity = DbEntity.parseEntity(type)!!
        val typeSpec = TypeSpec.classBuilder(classDeclaration.generatedClassName("CassandraParameterColumnMapper"))
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(CassandraTypes.parameterColumnMapper.parameterizedBy(typeName))
        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_stmt", CassandraTypes.settableByName)
            .addParameter("_index", Int::class.javaPrimitiveType!!.asTypeName())
            .addParameter("_value", typeName.copy(true))
        apply.controlFlow("if (_value == null)") {
            addStatement("_stmt.setToNull(_index)")
            addStatement("return")
        }
        apply.addStatement("val _type = _stmt.getType(_index) as %T", CassandraTypes.userDefinedType)
        apply.addStatement("val _object = _type.newValue()")
        apply.addCode("\n")
        addMappers(entity, typeSpec, constructor)
        getIndexes(entity, apply)
        setObject(entity, apply)
        apply.addStatement("_stmt.setUdtValue(_index, _object)")
        typeSpec.addFunction(apply.build())
        typeSpec.primaryConstructor(constructor.build())

        FileSpec.get(classDeclaration.packageName.asString(), typeSpec.build()).writeTo(environment.codeGenerator, false, listOfNotNull(classDeclaration.containingFile))
    }

    private fun generateListSetter(resolver: Resolver, classDeclaration: KSClassDeclaration) {
        val type = classDeclaration.asType(listOf())
        val typeName = type.toTypeName()
        val listTypeName = LIST.parameterizedBy(typeName.copy(false)).copy(false)
        val entity = DbEntity.parseEntity(type)!!
        val typeSpec = TypeSpec.classBuilder(classDeclaration.generatedClassName("List_CassandraParameterColumnMapper"))
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(CassandraTypes.parameterColumnMapper.parameterizedBy(listTypeName))
        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("_stmt", CassandraTypes.settableByName)
            .addParameter("_index", Int::class.javaPrimitiveType!!.asTypeName())
            .addParameter("_listValue", listTypeName.copy(true))
        apply.controlFlow("if (_listValue == null)") {
            addStatement("_stmt.setToNull(_index)")
            addStatement("return")
        }
        apply.addStatement("val _listType = _stmt.getType(_index) as %T", CassandraTypes.listType)
        apply.addStatement("val _type = _listType.getElementType() as %T", CassandraTypes.userDefinedType)
        getIndexes(entity, apply)
        addMappers(entity, typeSpec, constructor)
        typeSpec.primaryConstructor(constructor.build())
        apply.controlFlow("val _udtList = _listValue.map { _value ->") {
            apply.addStatement("val _object = _type.newValue()")
            apply.addCode("\n")
            setObject(entity, apply)
            addStatement("_object")
        }
        apply.addStatement("_stmt.setList(_index, _udtList, %T::class.java)", CassandraTypes.udtValue)
        typeSpec.addFunction(apply.build())

        FileSpec.get(classDeclaration.packageName.asString(), typeSpec.build()).writeTo(environment.codeGenerator, false, listOfNotNull(classDeclaration.containingFile))
    }

    private fun setObject(entity: DbEntity, apply: FunSpec.Builder) {
        for (field in entity.fields) {
            val fieldName = field.property.simpleName.asString()
            val fieldTypeName = field.type.toTypeName()
            apply.controlFlow("_value.%N.let {", fieldName) {
                if (field.type.isMarkedNullable) {
                    beginControlFlow("if (it == null)")
                    addStatement("_object.setToNull(%N)", "_index_of_$fieldName")
                    nextControlFlow("else")
                }
                val nativeType = CassandraNativeTypes.findNativeType(fieldTypeName)
                if (nativeType != null) {
                    addCode(nativeType.bind("_object", CodeBlock.of("it"), CodeBlock.of("%N", "_index_of_$fieldName")))
                    addCode("\n")
                } else {
                    val mapperName = "_${fieldName}_mapper"
                    addStatement("this.%N.apply(_object, %N, it)", mapperName, "_index_of_$fieldName")
                }
                if (field.type.isMarkedNullable) {
                    endControlFlow()
                }
            }
        }
    }

    fun getIndexes(entity: DbEntity, apply: FunSpec.Builder) {
        for (field in entity.columns) {
            val fieldName = field.property.simpleName.asString()
            apply.addStatement("val %N = _type.firstIndexOf(%S)", "_index_of_$fieldName", field.columnName)
        }
    }

    fun addMappers(entity: DbEntity, typeSpec: TypeSpec.Builder, constructor: FunSpec.Builder) {
        for (field in entity.fields) {
            val fieldName = field.property.simpleName.asString()
            val fieldTypeName = field.type.toTypeName()
            val nativeType = CassandraNativeTypes.findNativeType(fieldTypeName)
            if (nativeType == null) {
                val mapperName = "_${fieldName}_mapper"
                val mapperType = CassandraTypes.parameterColumnMapper.parameterizedBy(fieldTypeName.copy(false))
                constructor.addParameter(mapperName, mapperType)
                constructor.addStatement("this.%N = %N", mapperName, mapperName)

                typeSpec.addProperty(mapperName, mapperType, KModifier.PRIVATE, KModifier.FINAL)
            }
        }
    }
}

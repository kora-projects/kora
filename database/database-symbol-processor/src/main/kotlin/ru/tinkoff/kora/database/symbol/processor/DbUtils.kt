package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.database.symbol.processor.model.QueryParameter
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.parseMappingData

object DbUtils {

    val queryContext = ClassName("ru.tinkoff.kora.database.common", "QueryContext")
    val repositoryAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Repository")
    val columnAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Column")
    val entityConstructorAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "EntityConstructor")
    val queryAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Query")
    val idAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Id")
    val tableAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Table")
    val batchAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Batch")
    val embeddedAnnotation = ClassName("ru.tinkoff.kora.database.common.annotation", "Embedded")
    val updateCount = ClassName("ru.tinkoff.kora.database.common", "UpdateCount")

    val awaitSingleOrNull = MemberName("kotlinx.coroutines.reactor", "awaitSingleOrNull")
    val awaitSingle = MemberName("kotlinx.coroutines.reactor", "awaitSingle")
    val asFlow = MemberName("kotlinx.coroutines.reactive", "asFlow")

    fun KSFunctionDeclaration.operationName() = this.parentDeclaration?.simpleName?.asString() + "." + this.simpleName.asString()

    fun FieldFactory.addMapper(mapper: Mapper): String {
        if (mapper.mapperType == null) {
            return add(mapper.fieldTypeName, mapper.tags)
        } else {
            val name = add(mapper.mapperType, mapper.tags)
            if (mapper.wrapper == null) {
                return name
            }
            return add(mapper.fieldTypeName, mapper.wrapper.invoke(CodeBlock.of("%N", name)))
        }
    }

    fun addMappers(type: TypeSpec.Builder, constructor: FunSpec.Builder, mappers: List<Mapper>) {
        var companion = type.typeSpecs.asSequence().filter { it.isCompanion }.firstOrNull()?.toBuilder()
        for (mapper in mappers) {
            if (mapper.mapperType == null) {
                type.addProperty(mapper.fieldName, mapper.fieldTypeName, KModifier.PRIVATE)
                constructor.addParameter(
                    ParameterSpec.builder(mapper.fieldName, mapper.fieldTypeName)
                        .addTag(mapper.tags)
                        .build()
                )
                constructor.addCode("this.`%L` = `%L`;\n", mapper.fieldName, mapper.fieldName)
            } else if (hasDefaultConstructor(mapper.mapperType)) {
                if (companion == null) {
                    companion = TypeSpec.companionObjectBuilder(null)
                        .generated(RepositorySymbolProcessor::class)
                }
                if (mapper.wrapper == null) {
                    val property = PropertySpec.builder(mapper.fieldName, mapper.mapperType.toTypeName(), KModifier.PRIVATE)
                    companion.addProperty(property.initializer("%T()", (mapper.mapperType.declaration as KSClassDeclaration).toClassName()).build())
                } else {
                    val property = PropertySpec.builder(mapper.fieldName, mapper.fieldTypeName, KModifier.PRIVATE)
                    companion.addProperty(property.initializer(mapper.wrapper.invoke(CodeBlock.of("%T()", mapper.mapperType.toTypeName()))).build())
                }
            } else {
                val mapperType = mapper.mapperType.toTypeName()
                constructor.addParameter(mapper.fieldName, mapperType)
                if (mapper.wrapper != null) {
                    type.addProperty(mapper.fieldName, mapper.fieldTypeName, KModifier.PRIVATE)
                    constructor.addCode("this.%N = %L\n", mapper.fieldName, mapper.wrapper.invoke(CodeBlock.of("%N", mapper.fieldName)))
                } else {
                    type.addProperty(mapper.fieldName, mapperType, KModifier.PRIVATE)
                    constructor.addCode("this.%N = %N\n", mapper.fieldName, mapper.fieldName)
                }
            }
        }
        if (companion != null) {
            type.typeSpecs.removeIf { it.isCompanion }
            type.addType(companion.build())
        }
    }

    fun KSClassDeclaration.parseExecutorTag(): CodeBlock? {
        val repository = this.findAnnotation(repositoryAnnotation)!!
        val executorTag = repository.findValue<KSAnnotation>("executorTag")
        if (executorTag == null) {
            return null
        }
        val value = executorTag.findValue<List<KSType>>("value")!!
        if (value.isEmpty()) {
            return null
        }
        val codeBlock = CodeBlock.builder().add("[")
        value.forEachIndexed { i, type ->
            if (i > 0) {
                codeBlock.add(", ")
            }
            codeBlock.add("%T::class", (type.declaration as KSClassDeclaration).toClassName())
        }
        return codeBlock.add("]").build()
    }

    fun KSClassDeclaration.findQueryMethods() = this.getAllFunctions()
        .filter { it.isAbstract }
        .filter { it.findAnnotation(DbUtils.queryAnnotation) != null }

    fun parseParameterMappers(
        method: KSFunctionDeclaration,
        parameters: List<QueryParameter>,
        query: QueryWithParameters,
        parameterColumnMapper: ClassName,
        nativeTypePredicate: (KSType) -> Boolean
    ): List<Mapper> {
        val mappers = ArrayList<Mapper>()
        for (p in parameters) {
            var parameter = p
            if (parameter is QueryParameter.ConnectionParameter) {
                continue
            }
            if (parameter is QueryParameter.BatchParameter) {
                parameter = parameter.parameter
            }
            val parameterType = parameter.type
            val mappings = parameter.variable.parseMappingData()
            val mapping = mappings.getMapping(parameterColumnMapper)
            if (mapping != null) {
                val mapperName = parameterMapperName(method, parameter.variable)
                val mapperType = parameterColumnMapper.parameterizedBy(parameterType.toTypeName().copy(false))
                mappers.add(Mapper(mapping, mapperType, mapperName))
                continue
            }
            if (parameter is QueryParameter.SimpleParameter) {
                if (!nativeTypePredicate(parameter.type)) {
                    val mapperName = parameterMapperName(method, parameter.variable)
                    val mapperType = parameterColumnMapper.parameterizedBy(parameterType.toTypeName().copy(false))
                    mappers.add(Mapper(mapperType, mapperName))
                }
                continue
            }
            if (parameter is QueryParameter.EntityParameter) {
                for (entityField in parameter.entity.columns) {
                    val queryParam = query.find(parameter.name + "." + entityField.property.simpleName.getShortName())
                    if (queryParam == null || queryParam.sqlIndexes.isEmpty()) {
                        continue
                    }
                    val mapperName = parameterMapperName(method, parameter.variable, entityField.property.simpleName.getShortName())
                    val mapperType = parameterColumnMapper.parameterizedBy(entityField.type.toTypeName().copy(false))
                    val fieldMappings = entityField.mapping
                    val fieldMapping = fieldMappings.getMapping(parameterColumnMapper)
                    if (fieldMapping != null) {
                        mappers.add(Mapper(fieldMapping, mapperType, mapperName))
                    } else if (!nativeTypePredicate(entityField.type)) {
                        mappers.add(Mapper(mapperType, mapperName))
                    }
                }

                val queryParam = query.find(parameter.name)
                if (queryParam == null || queryParam.sqlIndexes.isEmpty()) {
                    continue
                }

                val mapperName = parameterMapperName(method, parameter.variable, parameter.name)
                val mapperType = parameterColumnMapper.parameterizedBy(parameter.entity.type.toTypeName().copy(false))
                val fieldMappings = parameter.entity.classDeclaration.parseMappingData()
                val fieldMapping = fieldMappings.getMapping(parameterColumnMapper)
                if (fieldMapping != null) {
                    mappers.add(Mapper(fieldMapping, mapperType, mapperName))
                } else {
                    mappers.add(Mapper(mapperType, mapperName))
                }
            }
        }
        return mappers
    }


    fun hasDefaultConstructor(type: KSType): Boolean {
        val typeDeclaration = type.declaration as KSClassDeclaration
        val primaryConstructor = typeDeclaration.primaryConstructor
        return !typeDeclaration.isOpen() && primaryConstructor != null && primaryConstructor.parameters.isEmpty()
    }

    fun parameterMapperName(method: KSFunctionDeclaration, parameter: KSValueParameter, vararg names: String): String {
        val sb = StringBuilder("_" + method.simpleName.asString() + "_" + parameter.name!!.asString())
        for (name in names) {
            sb.append("_").append(name)
        }
        return sb.append("_parameterMapper").toString()
    }

    fun KSFunctionDeclaration.resultMapperName() = "_${this.simpleName.asString()}_resultMapper"


    fun KSFunctionDeclaration.queryMethodBuilder(resolver: Resolver): FunSpec.Builder {
        return overridingKeepAop(resolver);
    }
}


// todo tags
data class Mapper(val mapperType: KSType?, val tags: Set<String>, val fieldTypeName: TypeName, val fieldName: String, val wrapper: ((CodeBlock) -> CodeBlock)?) {
    constructor(typeName: TypeName, name: String) : this(null, setOf(), typeName, name, null)
    constructor(mapping: MappingData, typeName: TypeName, name: String) : this(mapping.mapper, mapping.tags, typeName, name, null)
    constructor(mapping: MappingData, typeName: TypeName, name: String, wrapper: ((CodeBlock) -> CodeBlock)?) : this(mapping.mapper, mapping.tags, typeName, name, wrapper)
}



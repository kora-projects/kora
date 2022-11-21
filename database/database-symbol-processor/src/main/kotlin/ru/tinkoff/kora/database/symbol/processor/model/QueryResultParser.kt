package ru.tinkoff.kora.database.symbol.processor.model
/*
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.naming.NameConverter
import ru.tinkoff.kora.database.common.RowMapper
import ru.tinkoff.kora.database.symbol.processor.RepositoryGenerator
import ru.tinkoff.kora.database.symbol.processor.findEntityConstructor
import ru.tinkoff.kora.database.symbol.processor.parseColumnName
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getNameConverter
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.util.*

@KspExperimental
@KotlinPoetKspPreview
class QueryResultParser(private val resolver: Resolver) {
    private val monoType = resolver.getClassDeclarationByName(Mono::class.qualifiedName!!)!!.asStarProjectedType()
    private val fluxType = resolver.getClassDeclarationByName(Flux::class.qualifiedName!!)!!.asStarProjectedType()
    private val listType = resolver.getClassDeclarationByName(List::class.qualifiedName!!)!!.asStarProjectedType()
    private val rowMapperType = resolver.getClassDeclarationByName(RowMapper::class.qualifiedName!!)?.asStarProjectedType()

    fun parse(gen: RepositoryGenerator, repository: KSClassDeclaration, method: KSFunctionDeclaration): QueryResult {
        val mappings = parseMappingData(method)
        val mapping = gen.resultMapperTypes()
            .mapNotNull { m: KSType -> mappings.getMapping(m) }
            .firstOrNull { m -> m.mapper != null }
        val executableType = method.asMemberOf(repository.asStarProjectedType())
        val type = executableType.returnType!!
        // type parameter is required for the majority of return types so let us just take it right now
        val typeParameter = typeParameter(type)
        if (method.modifiers.contains(Modifier.SUSPEND)){
            val suspendResult = parseType(gen, method, mapping, type, true)
            return if (suspendResult is QueryResult.SimpleResult || suspendResult is QueryResult.DtoResult || suspendResult is QueryResult.ResultWithMapper || suspendResult is QueryResult.ListResult) {
                QueryResult.SuspendResult(type, suspendResult)
            } else {
                throw ProcessingErrorException("Return type of method is invalid", method)
            }
        }
        if (isMono(type)) {
            assert(typeParameter != null)
            val monoOf: QueryResult = parseType(gen, method, mapping, typeParameter!!, true)
            return if (monoOf is QueryResult.SimpleResult || monoOf is QueryResult.DtoResult || monoOf is QueryResult.ResultWithMapper || monoOf is QueryResult.ListResult) {
                QueryResult.MonoResult(type, monoOf)
            } else {
                throw ProcessingErrorException("Return type of method is invalid", method)
            }
        }
        return parseType(gen, method, mapping, type, true)
    }

    private fun parseType(
        gen: RepositoryGenerator,
        source: KSDeclaration,
        mapping: MappingData?,
        type: KSType,
        parseDto: Boolean
    ): QueryResult {
        if (isList(type)) {
            val typeParameter = typeParameter(type)
            if (mapping?.mapper != null) {
                return if (rowMapperType!!.isAssignableFrom(mapping.mapper!!)) {
                    val withMapper = QueryResult.ResultWithMapper(typeParameter!!, mapping)
                    QueryResult.ListResult(type, withMapper)
                } else {
                    QueryResult.ResultWithMapper(type, mapping)
                }
            }
            val listOf = parseType(gen, source, mapping, typeParameter!!, parseDto)
            return if (listOf is QueryResult.SimpleResult || listOf is QueryResult.DtoResult || listOf is QueryResult.ResultWithMapper) {
                QueryResult.ListResult(type, listOf)
            } else {
                throw ProcessingErrorException("Return type of method is invalid", source)
            }
        }
        if (mapping != null) {
            return QueryResult.ResultWithMapper(type, mapping)
        }
        for (nativeType in gen.nativeReturnTypes()) {
            val isNative = type == nativeType
            if (isNative) {
                return QueryResult.SimpleResult(type)
            }
        }
        if (parseDto) {
            return parseDtoType(gen, source, type)
        }
        throw ProcessingErrorException("Return type of method is invalid, type %s is not a native type".format(type), source)
    }

    private fun parseDtoType(gen: RepositoryGenerator, source: KSDeclaration, type: KSType): QueryResult.DtoResult {
        if (type.declaration !is KSClassDeclaration) {
            throw ProcessingErrorException("Return type of method is invalid", source)
        }
        val declaration = type.declaration as KSClassDeclaration
        val constructor = findEntityConstructor(declaration)
        val columnsNameConverter = getNameConverter(declaration)

        val immutable = constructor.parameters.isNotEmpty()
        val columns = if (immutable) {
            parseImmutableEntityColumns(gen, constructor, declaration, columnsNameConverter)
        } else {
            parseMutableEntityColumns(gen, declaration, columnsNameConverter)
        }
        return QueryResult.DtoResult(type, immutable, columns)
    }

    private fun parseMutableEntityColumns(gen: RepositoryGenerator, entity: KSClassDeclaration, columnsNameConverter: NameConverter?): List<QueryResult.DtoResult.DtoResultField> {
        val properties = entity.getDeclaredProperties().toList()

        val result = mutableListOf<QueryResult.DtoResult.DtoResultField>()
        for (field in properties) {
            val fieldType = field.type.resolve()
            field.setter ?: continue
            val sqlName = parseColumnName(field, columnsNameConverter)
            val mappingData = parseMappingData(field)
            val mapping = gen.dtoResultFieldMapperTypes()
                .map { mappingData.getMapping(it) }
                .firstOrNull { it?.mapper != null }

            val type = parseType(gen, field, mapping, fieldType, false)
            val name = field.simpleName.asString()

            result.add(QueryResult.DtoResult.DtoResultField(name, sqlName, field, type))
        }
        return result
    }


    private fun parseImmutableEntityColumns(
        gen: RepositoryGenerator,
        constructor: KSFunctionDeclaration,
        entityDeclaration: KSClassDeclaration,
        columnsNameConverter: NameConverter?
    ): List<QueryResult.DtoResult.DtoResultField> {
        val constructorParameters = constructor.parameters
        val result = mutableListOf<QueryResult.DtoResult.DtoResultField>()
        val entityType = entityDeclaration.asStarProjectedType()
        val constructorType = constructor.asMemberOf(entityType)
        constructorParameters.forEachIndexed { i, constructorParameter ->
            val columnType = constructorType.parameterTypes[i]!!
            val sqlName = parseColumnName(constructorParameter, columnsNameConverter)
            val name = constructorParameter.name!!.asString()
            val mappingData = parseMappingData(constructorParameter)
            val mapping = gen.dtoResultFieldMapperTypes()
                .map { mappingData.getMapping(it) }
                .firstOrNull { m -> m?.mapper != null }

            val propertyDeclaration = entityDeclaration.getDeclaredProperties().first { it.simpleName.asString() == name }

            val type = parseType(gen, propertyDeclaration, mapping, columnType, false)
            result.add(QueryResult.DtoResult.DtoResultField(name, sqlName, propertyDeclaration, type))
        }
        return result
    }

    private fun findMutator(type: KSClassDeclaration, field: KSValueParameter): KSPropertySetter? {
        val property = type.getDeclaredProperties().firstOrNull { it.simpleName.asString() == field.name!!.asString() }
        return property?.setter
    }

    private fun isMono(type: KSType): Boolean {
        return monoType.isAssignableFrom(type)
    }

    private fun isFlux(type: KSType): Boolean {
        return fluxType.isAssignableFrom(type)
    }

    private fun isList(type: KSType): Boolean {
        return listType.isAssignableFrom(type)
    }

    private fun typeParameter(type: KSType): KSType? {
        return type.arguments.firstOrNull()?.type?.resolve()
    }
}


 */

package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.FieldFactory
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.exception.ProcessingErrorException

/**
 * Emits the body of a single repository operation: it builds the BSON documents from templates, hands them to the
 * driver, and maps the driver result to the method return type. Telemetry and the surrounding scope are added by
 * [MongoRepositoryGenerator].
 */
class MongoOperationGenerator(private val resolver: Resolver) {

    class Context(
        val repository: KSClassDeclaration,
        val method: KSFunctionDeclaration,
        val returnType: KSType,
        val operation: MongoOperation,
        val parameters: MongoParameters,
        val codecs: FieldFactory,
        val registries: MongoCodecRegistries
    )

    companion object {
        private const val EXECUTOR = MongoRepositoryGenerator.EXECUTOR_FIELD
    }

    /**
     * @return a human readable description of the operation, used as the query text in telemetry
     */
    fun generate(b: CodeBlock.Builder, ctx: Context): String {
        val batch = ctx.parameters.batchParameter()
        if (batch != null) {
            return when (ctx.operation.kind) {
                MongoOperation.Kind.UPDATE -> this.generateUpdateBatch(b, ctx, batch)
                MongoOperation.Kind.REPLACE -> this.generateReplaceBatch(b, ctx, batch)
                MongoOperation.Kind.DELETE -> this.generateDeleteBatch(b, ctx, batch)
                else -> throw ProcessingErrorException(
                    """
                    Mongo repository method is invalid:
                      ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()}

                    Problem:
                      @Batch is not supported for @${ctx.operation.kind.annotationName.simpleName}.

                    Hint:
                      A batch turns into a single bulkWrite, which only covers update, replace and delete.
                      @MongoInsert already writes a whole collection of documents with insertMany.

                    Fix:
                      Remove @Batch, or switch to an operation that supports it.
                    """.trimIndent(), ctx.method
                )
            }
        }
        return this.generateSingle(b, ctx)
    }

    private fun generateSingle(b: CodeBlock.Builder, ctx: Context): String = when (ctx.operation.kind) {
        MongoOperation.Kind.FIND -> this.generateFind(b, ctx)
        MongoOperation.Kind.AGGREGATE -> this.generateAggregate(b, ctx)
        MongoOperation.Kind.COUNT -> this.generateCount(b, ctx)
        MongoOperation.Kind.INSERT -> this.generateInsert(b, ctx)
        MongoOperation.Kind.UPDATE -> this.generateUpdate(b, ctx)
        MongoOperation.Kind.REPLACE -> this.generateReplace(b, ctx)
        MongoOperation.Kind.DELETE -> this.generateDelete(b, ctx)
    }

    private fun generateFind(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val filter = ctx.operation.string("filter")
        val projection = ctx.operation.stringOrNull("projection")
        val sort = ctx.operation.stringOrNull("sort")
        val limit = this.intAttribute(ctx, ctx.operation.stringOrNull("limit"), "limit")
        val skip = this.intAttribute(ctx, ctx.operation.stringOrNull("skip"), "skip")

        val entityType = this.resultEntityType(ctx)
        val collection = this.resolveCollection(ctx, entityType)

        this.typedCollection(b, ctx, collection, entityType)
        b.addStatement("val _filter = %L", BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver))
        val projectionCode = if (projection == null) {
            MongoProjections.derive(entityType)
        } else {
            val template = BsonTemplate.parseDocument(projection, ctx.method, "projection")
            MongoProjections.validate(ctx.method, ctx.repository, entityType, template)
            template.toCodeBlock(resolver)
        }
        val sortCode = sort?.let { BsonTemplate.parseDocument(it, ctx.method, "sort").toCodeBlock(resolver) }
        ctx.parameters.validateAllUsed()

        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement("var _iterable = if (_session == null) _collection.find(_filter) else _collection.find(_session, _filter)")
            if (projectionCode != null) {
                addStatement("_iterable = _iterable.projection(%L)", projectionCode)
            }
            if (sortCode != null) {
                addStatement("_iterable = _iterable.sort(%L)", sortCode)
            }
            if (skip != null) {
                addStatement("_iterable = _iterable.skip(%L)", skip)
            }
            if (limit != null) {
                addStatement("_iterable = _iterable.limit(%L)", limit)
            }
            emitIterableResult(this, ctx, entityType)
            closeTry(this)
        }

        return "find $collection $filter"
    }

    private fun generateAggregate(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val pipeline = ctx.operation.string("value")

        val entityType = this.resultEntityType(ctx)
        val collection = this.resolveCollection(ctx, entityType)

        this.typedCollection(b, ctx, collection, entityType)
        b.addStatement("val _pipeline = %L", BsonTemplate.parseArray(pipeline, ctx.method, "value").toPipelineCodeBlock(resolver, ctx.method))
        ctx.parameters.validateAllUsed()

        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement("val _iterable = if (_session == null) _collection.aggregate(_pipeline) else _collection.aggregate(_session, _pipeline)")
            emitIterableResult(this, ctx, entityType)
            closeTry(this)
        }

        return "aggregate $collection $pipeline"
    }

    private fun generateCount(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val filter = ctx.operation.string("filter")
        val collection = this.resolveCollection(ctx, null)

        b.addStatement("val _collection = this.%N.database().getCollection(%S)", EXECUTOR, collection)
        b.addStatement("val _filter = %L", BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver))
        ctx.parameters.validateAllUsed()

        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement("val _count = if (_session == null) _collection.countDocuments(_filter) else _collection.countDocuments(_session, _filter)")
            emitCountResult(this, ctx, CodeBlock.of("_count"))
            closeTry(this)
        }

        return "count $collection $filter"
    }

    /**
     * The declared return type of `@MongoInsert` determines what the generated code has to build. [InsertResult.Entity]
     * carries the exact data (`MongoEntity` plus its `_id` field) that the emission methods need — so neither has to
     * re-derive it from a nullable value under a convention the compiler cannot check.
     */
    private sealed interface InsertResult {
        data object Nothing : InsertResult
        data object Id : InsertResult
        data class Entity(val entity: MongoEntity, val idField: MongoEntity.Field) : InsertResult
    }

    private fun generateInsert(b: CodeBlock.Builder, ctx: Context): String {
        val parameter = ctx.parameters.entityParameter("@MongoInsert")
        ctx.parameters.validateAllUsed()

        val elementType = collectionElementType(parameter.type)
        val many = elementType != null
        val entityType = elementType ?: parameter.type
        val collection = this.resolveCollection(ctx, entityType)
        val kind = this.insertResult(ctx, entityType, many)

        this.typedCollection(b, ctx, collection, entityType)
        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            if (many) {
                emitInsertMany(this, parameter, kind)
            } else {
                emitInsertOne(this, parameter, kind)
            }
            closeTry(this)
        }

        return "insert $collection"
    }

    private fun generateUpdate(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val filter = ctx.operation.string("filter")
        val update = ctx.operation.string("update")
        val upsert = ctx.operation.flag("upsert")
        val many = ctx.operation.flag("many")
        val collection = this.resolveCollection(ctx, null)

        b.addStatement("val _collection = this.%N.database().getCollection(%S)", EXECUTOR, collection)
        b.addStatement("val _filter = %L", BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver))
        b.addStatement("val _update = %L", BsonTemplate.parseDocument(update, ctx.method, "update").toCodeBlock(resolver))
        ctx.parameters.validateAllUsed()
        b.addStatement("val _options = %T().upsert(%L)", MongoTypes.updateOptions, upsert)

        val operation = if (many) "updateMany" else "updateOne"
        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement(
                "val _result = if (_session == null) _collection.%N(_filter, _update, _options) else _collection.%N(_session, _filter, _update, _options)",
                operation, operation
            )
            emitCountResult(this, ctx, CodeBlock.of("_result.modifiedCount + (if (_result.upsertedId != null) 1 else 0)"))
            closeTry(this)
        }

        return "update $collection $filter $update"
    }

    private fun generateReplace(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val filter = ctx.operation.string("filter")
        val upsert = ctx.operation.flag("upsert")

        val filterCode = BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver)
        val parameter = ctx.parameters.entityParameter("@MongoReplace")
        ctx.parameters.validateAllUsed()
        val collection = this.resolveCollection(ctx, parameter.type)

        this.typedCollection(b, ctx, collection, parameter.type)
        b.addStatement("val _filter = %L", filterCode)
        b.addStatement("val _options = %T().upsert(%L)", MongoTypes.replaceOptions, upsert)

        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement(
                "val _result = if (_session == null) _collection.replaceOne(_filter, %N, _options) else _collection.replaceOne(_session, _filter, %N, _options)",
                parameter.name, parameter.name
            )
            emitCountResult(this, ctx, CodeBlock.of("_result.modifiedCount + (if (_result.upsertedId != null) 1 else 0)"))
            closeTry(this)
        }

        return "replace $collection $filter"
    }

    private fun generateDelete(b: CodeBlock.Builder, ctx: Context): String {
        val resolver = ctx.parameters.resolver()
        val filter = ctx.operation.string("filter")
        val many = ctx.operation.flag("many")
        val collection = this.resolveCollection(ctx, null)

        b.addStatement("val _collection = this.%N.database().getCollection(%S)", EXECUTOR, collection)
        b.addStatement("val _filter = %L", BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver))
        ctx.parameters.validateAllUsed()

        val operation = if (many) "deleteMany" else "deleteOne"
        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement(
                "val _result = if (_session == null) _collection.%N(_filter) else _collection.%N(_session, _filter)",
                operation, operation
            )
            emitCountResult(this, ctx, CodeBlock.of("_result.deletedCount"))
            closeTry(this)
        }

        return "delete $collection $filter"
    }

    /**
     * A numeric attribute is either an integer literal or a `:name` of an Int parameter, which is what makes runtime
     * paging expressible.
     */
    private fun intAttribute(ctx: Context, value: String?, attribute: String): CodeBlock? {
        if (value == null) {
            return null
        }
        if (value.startsWith(":")) {
            return CodeBlock.of("%N", ctx.parameters.requireInt(value.substring(1), attribute).name)
        }
        val parsed = value.trim().toIntOrNull()
            ?: throw ProcessingErrorException(
                """
                Mongo repository method is invalid:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()}

                Problem:
                  Attribute '$attribute' is '$value', which is neither an integer nor a ':name' reference.

                Hint:
                  Write a literal such as 10, or ':size' to take the value from an Int method parameter.

                Fix:
                  Correct the attribute value.
                """.trimIndent(), ctx.method
            )
        return if (parsed <= 0) null else CodeBlock.of("%L", parsed)
    }

    private fun generateUpdateBatch(b: CodeBlock.Builder, ctx: Context, batch: MongoParameters.Parameter): String {
        val elementType = this.batchElementType(ctx, batch)
        val filter = ctx.operation.string("filter")
        val update = ctx.operation.string("update")
        val upsert = ctx.operation.flag("upsert")
        val many = ctx.operation.flag("many")
        val collection = this.resolveCollection(ctx, elementType)

        ctx.parameters.bindBatchElement(batch, "_b", elementType)
        val resolver = ctx.parameters.resolver()
        val filterCode = BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(resolver)
        val updateCode = BsonTemplate.parseDocument(update, ctx.method, "update").toCodeBlock(resolver)
        ctx.parameters.validateAllUsed()

        b.addStatement("val _collection = this.%N.database().getCollection(%S)", EXECUTOR, collection)
        b.addStatement("val _options = %T().upsert(%L)", MongoTypes.updateOptions, upsert)
        this.emitModels(
            b, batch, MongoTypes.document,
            CodeBlock.of("%T(%L, %L, _options)", if (many) MongoTypes.updateManyModel else MongoTypes.updateOneModel, filterCode, updateCode)
        )
        this.emitBulkWrite(b, ctx, CodeBlock.of("_result.modifiedCount.toLong() + _result.upserts.size"))

        return "bulk update $collection $filter $update"
    }

    private fun generateReplaceBatch(b: CodeBlock.Builder, ctx: Context, batch: MongoParameters.Parameter): String {
        val elementType = this.batchElementType(ctx, batch)
        val filter = ctx.operation.string("filter")
        val upsert = ctx.operation.flag("upsert")
        val collection = this.resolveCollection(ctx, elementType)

        ctx.parameters.bindBatchElement(batch, "_b", elementType)
        val filterCode = BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(ctx.parameters.resolver())
        ctx.parameters.validateAllUsed()

        this.typedCollection(b, ctx, collection, elementType)
        b.addStatement("val _options = %T().upsert(%L)", MongoTypes.replaceOptions, upsert)
        this.emitModels(
            b, batch, elementType.toTypeName().copy(false),
            CodeBlock.of("%T(%L, _b, _options)", MongoTypes.replaceOneModel, filterCode)
        )
        this.emitBulkWrite(b, ctx, CodeBlock.of("_result.modifiedCount.toLong() + _result.upserts.size"))

        return "bulk replace $collection $filter"
    }

    private fun generateDeleteBatch(b: CodeBlock.Builder, ctx: Context, batch: MongoParameters.Parameter): String {
        val elementType = this.batchElementType(ctx, batch)
        val filter = ctx.operation.string("filter")
        val many = ctx.operation.flag("many")
        val collection = this.resolveCollection(ctx, elementType)

        ctx.parameters.bindBatchElement(batch, "_b", elementType)
        val filterCode = BsonTemplate.parseDocument(filter, ctx.method, "filter").toCodeBlock(ctx.parameters.resolver())
        ctx.parameters.validateAllUsed()

        b.addStatement("val _collection = this.%N.database().getCollection(%S)", EXECUTOR, collection)
        this.emitModels(
            b, batch, MongoTypes.document,
            CodeBlock.of("%T(%L)", if (many) MongoTypes.deleteManyModel else MongoTypes.deleteOneModel, filterCode)
        )
        this.emitBulkWrite(b, ctx, CodeBlock.of("_result.deletedCount.toLong()"))

        return "bulk delete $collection $filter"
    }

    private fun emitModels(b: CodeBlock.Builder, batch: MongoParameters.Parameter, documentType: com.squareup.kotlinpoet.TypeName, model: CodeBlock) {
        b.addStatement("val _models = %T<%T<%T>>(%N.size)", ClassNames.arrayList, MongoTypes.writeModel, documentType, batch.name)
        b.controlFlow("for (_b in %N)", batch.name) {
            addStatement("_models.add(%L)", model)
        }
    }

    /**
     * An empty batch is not sent: the driver rejects a bulkWrite with no models.
     */
    private fun emitBulkWrite(b: CodeBlock.Builder, ctx: Context, affected: CodeBlock) {
        b.addStatement("_observation.observeStatement()")
        b.controlFlow("try") {
            addStatement("val _session = this.%N.currentSession()", EXECUTOR)
            addStatement("var _affected = 0L")
            controlFlow("if (_models.isNotEmpty())") {
                addStatement("val _result = if (_session == null) _collection.bulkWrite(_models) else _collection.bulkWrite(_session, _models)")
                addStatement("_affected = %L", affected)
            }
            emitCountResult(this, ctx, CodeBlock.of("_affected"))
            closeTry(this)
        }
    }

    private fun batchElementType(ctx: Context, batch: MongoParameters.Parameter): KSType =
        collectionElementType(batch.type)
            ?: throw ProcessingErrorException(
                """
                Mongo repository method is invalid:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()}

                Problem:
                  Parameter '${batch.name}' is annotated with @Batch but is not a List, Set or Collection.

                Hint:
                  A batch operation walks a collection and turns every element into one bulkWrite model.

                Fix:
                  Declare the parameter as a collection, or remove @Batch.
                """.trimIndent(), ctx.method
            )

    private fun typedCollection(b: CodeBlock.Builder, ctx: Context, collection: String, entityType: KSType) {
        val codecField = ctx.codecs.add(MongoTypes.codec.parameterizedBy(entityType.toTypeName().copy(false)), null as String?)
        val registry = ctx.registries.forCodec(codecField)
        b.addStatement(
            "val _collection = this.%N.database().getCollection(%S, %T::class.java).withCodecRegistry(this.%N)",
            EXECUTOR, collection, entityType.toTypeName().copy(false), registry
        )
    }

    private fun closeTry(b: CodeBlock.Builder) {
        b.nextControlFlow("catch (_e: Throwable)")
        b.addStatement("_observation.observeError(_e)")
        b.addStatement("throw _e")
        b.nextControlFlow("finally")
        b.addStatement("_observation.end()")
    }

    private fun emitIterableResult(b: CodeBlock.Builder, ctx: Context, entityType: KSType) {
        val returnType = ctx.returnType
        if (returnType.declaration.qualifiedName?.asString() == "kotlin.collections.List") {
            b.addStatement("_iterable.into(%T<%T>())", ClassNames.arrayList, entityType.toTypeName())
        } else if (returnType.isMarkedNullable) {
            b.addStatement("_iterable.first()")
        } else {
            b.addStatement(
                "_iterable.first() ?: throw %T(%S)", NoSuchElementException::class,
                "${ctx.repository.simpleName.asString()}.${ctx.method.simpleName.asString()} found no document"
            )
        }
    }

    private fun emitCountResult(b: CodeBlock.Builder, ctx: Context, count: CodeBlock) {
        val returnType = ctx.returnType
        if (returnType == this.resolver.builtIns.unitType) {
            return
        }
        when (returnType.declaration.qualifiedName?.asString()) {
            DbUtils.updateCount.canonicalName -> b.addStatement("%T(%L)", DbUtils.updateCount, count)
            "kotlin.Long" -> b.addStatement("%L", count)
            "kotlin.Int" -> b.addStatement("(%L).toInt()", count)
            else -> throw ProcessingErrorException(
                """
                Mongo repository method has an unsupported return type:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns $returnType

                Problem:
                  This operation reports how many documents it affected.

                Hint:
                  Supported return types are Unit, UpdateCount, Long and Int.

                Fix:
                  Change the return type to one of the supported ones.
                """.trimIndent(), ctx.method
            )
        }
    }

    private fun insertResult(ctx: Context, entityType: KSType, many: Boolean): InsertResult {
        val returnType = ctx.returnType
        if (returnType == this.resolver.builtIns.unitType) {
            return InsertResult.Nothing
        }

        val declared = if (many) collectionElementType(returnType) else returnType
        val isList = !many || returnType.declaration.qualifiedName?.asString() == "kotlin.collections.List"
        if (declared != null && isList) {
            if (declared.declaration.qualifiedName?.asString() == MongoTypes.objectId.canonicalName) {
                this.requireObjectIdOrNoId(ctx, this.tryParseEntity(entityType))
                return InsertResult.Id
            }
            if (declared.declaration.qualifiedName?.asString() == entityType.declaration.qualifiedName?.asString()) {
                return this.requireIdFieldOfObjectId(ctx, entityType, this.tryParseEntity(entityType))
            }
        }

        throw ProcessingErrorException(
            """
            Mongo repository method has an unsupported return type:
              ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns $returnType

            Problem:
              @MongoInsert can report the identifiers it wrote, or nothing at all.

            Hint:
              Supported return types are Unit, ObjectId and the entity type; for a collection parameter,
              List<ObjectId> and List<Entity>.

            Fix:
              Change the return type to one of the supported ones.
            """.trimIndent(), ctx.method
        )
    }

    /**
     * A type without a primary constructor is not a Kotlin entity Kora can introspect — insert results built from it
     * fall back to the checks that also cover a missing `_id` field.
     */
    private fun tryParseEntity(entityType: KSType): MongoEntity? {
        val declaration = entityType.declaration as? KSClassDeclaration ?: return null
        if (declaration.primaryConstructor == null) return null
        return MongoEntity.parse(declaration, this.resolver)
    }

    private fun requireObjectIdOrNoId(ctx: Context, entity: MongoEntity?) {
        if (entity == null) {
            return
        }
        val id = entity.idField ?: return
        if (id.type.declaration.qualifiedName?.asString() != MongoTypes.objectId.canonicalName) {
            throw ProcessingErrorException(
                """
                Mongo repository method has an unsupported return type:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns ${ctx.returnType}

                Problem:
                  Entity ${entity.declaration.qualifiedName?.asString()} maps '_id' to a ${id.type}, so the inserted identifier is not an ObjectId.

                Hint:
                  A server-generated identifier is always an ObjectId; a hand-assigned one keeps the type of the field.

                Fix:
                  Declare the method Unit, or make the '_id' field an ObjectId.
                """.trimIndent(), ctx.method
            )
        }
    }

    private fun requireIdFieldOfObjectId(ctx: Context, entityType: KSType, entity: MongoEntity?): InsertResult.Entity {
        val idField = entity?.idField
        if (entity == null || idField == null) {
            throw ProcessingErrorException(
                """
                Mongo repository method has an unsupported return type:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns ${ctx.returnType}

                Problem:
                  Entity ${entity?.declaration?.qualifiedName?.asString() ?: entityType} has no field mapped to '_id', so there is nowhere to write the generated identifier.

                Hint:
                  An entity result differs from its argument only by the identifier.

                Fix:
                  Add an @Id ObjectId field, or declare the method Unit or ObjectId.
                """.trimIndent(), ctx.method
            )
        }
        this.requireObjectIdOrNoId(ctx, entity)
        return InsertResult.Entity(entity, idField)
    }

    /**
     * The generated body runs inside a `ScopedValue...call { }` lambda (see [MongoRepositoryGenerator]), so a value
     * is produced by leaving it as the block's last expression — a bare `return` from inside that lambda is illegal.
     */
    private fun emitInsertOne(b: CodeBlock.Builder, parameter: MongoParameters.Parameter, kind: InsertResult) {
        when (kind) {
            InsertResult.Nothing -> b.controlFlow("if (_session == null)") {
                addStatement("_collection.insertOne(%N)", parameter.name)
                nextControlFlow("else")
                addStatement("_collection.insertOne(_session, %N)", parameter.name)
            }

            InsertResult.Id -> {
                this.emitInsertOneResult(b, parameter)
                b.addStatement("_id")
            }

            is InsertResult.Entity -> {
                this.emitInsertOneResult(b, parameter)
                b.addStatement("%L", kind.entity.rebuildWithId(CodeBlock.of("_id"), CodeBlock.of("%N", parameter.name)))
            }
        }
    }

    private fun emitInsertOneResult(b: CodeBlock.Builder, parameter: MongoParameters.Parameter) {
        b.addStatement(
            "val _result = if (_session == null) _collection.insertOne(%N) else _collection.insertOne(_session, %N)",
            parameter.name, parameter.name
        )
        b.addStatement("val _id = %T.insertedId(_result)", MongoTypes.results)
    }

    /**
     * The empty check and the insert itself are one `if`/`else` expression, not two statements with an early
     * `return`, for the same reason as [emitInsertOne]: a bare `return` from the wrapping lambda is illegal.
     */
    private fun emitInsertMany(b: CodeBlock.Builder, parameter: MongoParameters.Parameter, kind: InsertResult) {
        b.addStatement("val _documents = %N.toList()", parameter.name)
        b.controlFlow("if (_documents.isEmpty())") {
            if (kind != InsertResult.Nothing) {
                addStatement("emptyList()")
            }
            nextControlFlow("else")
            when (kind) {
                InsertResult.Nothing -> controlFlow("if (_session == null)") {
                    addStatement("_collection.insertMany(_documents)")
                    nextControlFlow("else")
                    addStatement("_collection.insertMany(_session, _documents)")
                }

                InsertResult.Id -> {
                    emitInsertManyResult(this)
                    addStatement("_ids")
                }

                is InsertResult.Entity -> {
                    emitInsertManyResult(this)
                    addStatement(
                        "_documents.mapIndexed { _i, _d -> %L }",
                        kind.entity.rebuildWithId(CodeBlock.of("_ids[_i]"), CodeBlock.of("_d"))
                    )
                }
            }
        }
    }

    private fun emitInsertManyResult(b: CodeBlock.Builder) {
        b.addStatement("val _result = if (_session == null) _collection.insertMany(_documents) else _collection.insertMany(_session, _documents)")
        b.addStatement("val _ids = %T.insertedIds(_result, _documents.size)", MongoTypes.results)
    }

    private fun resultEntityType(ctx: Context): KSType {
        val returnType = ctx.returnType
        if (returnType == this.resolver.builtIns.unitType) {
            throw ProcessingErrorException(
                """
                Mongo repository method has an unsupported return type:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns Unit

                Problem:
                  A read operation must return the documents it reads.

                Hint:
                  Supported return types are an entity, a nullable entity and List<Entity>.

                Fix:
                  Declare a return type for the method.
                """.trimIndent(), ctx.method
            )
        }
        val entityType = if (returnType.declaration.qualifiedName?.asString() == "kotlin.collections.List") {
            returnType.arguments.single().type!!.resolve()
        } else {
            returnType.makeNotNullable()
        }
        // no codec is registered for a container, so it would only fail when the graph is built
        val container = entityType.declaration.qualifiedName?.asString()
        if (container in containerTypes) {
            throw ProcessingErrorException(
                """
                Mongo repository method has an unsupported return type:
                  ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()} returns $returnType

                Problem:
                  A read operation returns an entity, a nullable entity or List<Entity>, and the entity can not itself be a collection or a map.

                Hint:
                  Every document is decoded by the codec of the entity type, and Kora has no codec for $container.

                Fix:
                  Return List<Entity> or Entity? with a concrete entity type.
                """.trimIndent(), ctx.method
            )
        }
        return entityType
    }

    private val containerTypes = setOf(
        "kotlin.collections.List", "kotlin.collections.MutableList", "kotlin.collections.Set", "kotlin.collections.MutableSet",
        "kotlin.collections.Collection", "kotlin.collections.MutableCollection", "kotlin.collections.Iterable", "kotlin.collections.MutableIterable",
        "kotlin.collections.Map", "kotlin.collections.MutableMap", "java.util.Optional"
    )

    private fun resolveCollection(ctx: Context, entityType: KSType?): String {
        ctx.operation.stringOrNull("collection")?.let { return it }

        ctx.repository.findAnnotation(MongoTypes.mongoCollection)
            ?.findValueNoDefault<String>("value")
            ?.let { return it }

        (entityType?.declaration as? KSClassDeclaration)
            ?.findAnnotation(MongoTypes.mongoCollection)
            ?.findValueNoDefault<String>("value")
            ?.let { return it }

        throw ProcessingErrorException(
            """
            Mongo collection can not be resolved:
              ${ctx.repository.simpleName.asString()}#${ctx.method.simpleName.asString()}

            Problem:
              The operation does not say which collection it works with.

            Hint:
              A collection is taken from the 'collection' attribute of the operation, then from @MongoCollection on the
              repository, then from @MongoCollection on the entity.

            Fix:
              Set the 'collection' attribute, or annotate the repository or the entity with @MongoCollection.
            """.trimIndent(), ctx.method
        )
    }

    private object ClassNames {
        val arrayList = com.squareup.kotlinpoet.ClassName("kotlin.collections", "ArrayList")
    }
}

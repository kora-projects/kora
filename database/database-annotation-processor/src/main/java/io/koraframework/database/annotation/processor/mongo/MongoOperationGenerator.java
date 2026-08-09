package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.FieldFactory;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.database.annotation.processor.DbUtils;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Emits the body of a single repository operation: it builds the BSON documents from templates, hands them to the
 * driver, and maps the driver result to the method return type. Telemetry and the surrounding scope are added by
 * {@link MongoRepositoryGenerator}.
 */
final class MongoOperationGenerator {

    private static final String EXECUTOR = MongoRepositoryGenerator.EXECUTOR_FIELD;

    record Context(TypeElement repository,
                   ExecutableElement method,
                   ExecutableType methodType,
                   MongoOperation operation,
                   MongoParameters parameters,
                   FieldFactory codecs,
                   MongoCodecRegistries registries) {}

    private final Types types;
    private final Elements elements;

    MongoOperationGenerator(Types types, Elements elements) {
        this.types = types;
        this.elements = elements;
    }

    /**
     * @return a human readable description of the operation, used as the query text in telemetry
     */
    String generate(CodeBlock.Builder b, Context ctx) {
        var batch = ctx.parameters().batchParameter();
        if (batch != null) {
            return switch (ctx.operation().kind()) {
                case UPDATE -> this.generateUpdateBatch(b, ctx, batch);
                case REPLACE -> this.generateReplaceBatch(b, ctx, batch);
                case DELETE -> this.generateDeleteBatch(b, ctx, batch);
                default -> throw new ProcessingErrorException("""
                    Mongo repository method is invalid:
                      %s#%s

                    Problem:
                      @Batch is not supported for @%s.

                    Hint:
                      A batch turns into a single bulkWrite, which only covers update, replace and delete.
                      @MongoInsert already writes a whole collection of documents with insertMany.

                    Fix:
                      Remove @Batch, or switch to an operation that supports it.
                    """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(),
                    ctx.operation().kind().annotationName().simpleName()), ctx.method());
            };
        }

        return switch (ctx.operation().kind()) {
            case FIND -> this.generateFind(b, ctx);
            case AGGREGATE -> this.generateAggregate(b, ctx);
            case COUNT -> this.generateCount(b, ctx);
            case INSERT -> this.generateInsert(b, ctx);
            case UPDATE -> this.generateUpdate(b, ctx);
            case REPLACE -> this.generateReplace(b, ctx);
            case DELETE -> this.generateDelete(b, ctx);
        };
    }

    private String generateFind(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var filter = ctx.operation().string(this.elements, "filter");
        var projection = ctx.operation().stringOrNull(this.elements, "projection");
        var sort = ctx.operation().stringOrNull(this.elements, "sort");
        var limit = this.intAttribute(ctx, ctx.operation().stringOrNull(this.elements, "limit"), "limit");
        var skip = this.intAttribute(ctx, ctx.operation().stringOrNull(this.elements, "skip"), "skip");

        var entityType = this.resultEntityType(ctx);
        var collection = this.resolveCollection(ctx, entityType);

        this.typedCollection(b, ctx, collection, entityType);
        b.addStatement("var _filter = $L", BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver));
        CodeBlock projectionCode;
        if (projection == null) {
            projectionCode = MongoProjections.derive(this.types, entityType);
        } else {
            var template = BsonTemplate.parseDocument(projection, ctx.method(), "projection");
            MongoProjections.validate(this.types, ctx.method(), ctx.repository(), entityType, template);
            projectionCode = template.toCodeBlock(resolver);
        }
        var sortCode = sort == null ? null : BsonTemplate.parseDocument(sort, ctx.method(), "sort").toCodeBlock(resolver);
        ctx.parameters().validateAllUsed();

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        b.addStatement("var _iterable = _session == null ? _collection.find(_filter) : _collection.find(_session, _filter)");
        if (projectionCode != null) {
            b.addStatement("_iterable = _iterable.projection($L)", projectionCode);
        }
        if (sortCode != null) {
            b.addStatement("_iterable = _iterable.sort($L)", sortCode);
        }
        if (skip != null) {
            b.addStatement("_iterable = _iterable.skip($L)", skip);
        }
        if (limit != null) {
            b.addStatement("_iterable = _iterable.limit($L)", limit);
        }
        this.emitIterableResult(b, ctx, entityType);
        this.closeTry(b);

        return "find " + collection + " " + filter;
    }

    private String generateAggregate(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var pipeline = ctx.operation().string(this.elements, "value");

        var entityType = this.resultEntityType(ctx);
        var collection = this.resolveCollection(ctx, entityType);

        this.typedCollection(b, ctx, collection, entityType);
        b.addStatement("var _pipeline = $L", BsonTemplate.parseArray(pipeline, ctx.method(), "value").toPipelineCodeBlock(resolver, ctx.method()));
        ctx.parameters().validateAllUsed();

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        b.addStatement("var _iterable = _session == null ? _collection.aggregate(_pipeline) : _collection.aggregate(_session, _pipeline)");
        this.emitIterableResult(b, ctx, entityType);
        this.closeTry(b);

        return "aggregate " + collection + " " + pipeline;
    }

    private String generateCount(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var filter = ctx.operation().string(this.elements, "filter");
        var collection = this.resolveCollection(ctx, null);

        this.rawCollection(b, collection);
        b.addStatement("var _filter = $L", BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver));
        ctx.parameters().validateAllUsed();

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        b.addStatement("var _count = _session == null ? _collection.countDocuments(_filter) : _collection.countDocuments(_session, _filter)");
        this.emitCountResult(b, ctx, CodeBlock.of("_count"));
        this.closeTry(b);

        return "count " + collection + " " + filter;
    }

    /**
     * The declared return type of {@code @MongoInsert} determines what the generated code has to build.
     * {@link InsertResult.Entity} carries the exact data ({@code MongoEntity} plus its {@code _id} field) that the
     * emission methods need — so neither has to re-derive it from a {@code @Nullable} value under a convention the
     * compiler cannot check.
     */
    private sealed interface InsertResult {
        record Nothing() implements InsertResult {}

        record Id() implements InsertResult {}

        record Entity(MongoEntity entity, MongoEntity.Field idField) implements InsertResult {}
    }

    private String generateInsert(CodeBlock.Builder b, Context ctx) {
        var parameter = ctx.parameters().entityParameter("@MongoInsert");
        ctx.parameters().validateAllUsed();

        var elementType = this.collectionElementType(parameter.type());
        var many = elementType != null;
        var entityType = many ? elementType : parameter.type();
        var collection = this.resolveCollection(ctx, entityType);
        var entity = MongoEntity.parse(this.types, entityType);
        var kind = this.insertResult(ctx, entityType, entity, many);

        this.typedCollection(b, ctx, collection, entityType);
        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        if (many) {
            this.emitInsertMany(b, parameter, entityType, kind);
        } else {
            this.emitInsertOne(b, parameter, kind);
        }
        this.closeTry(b);

        return "insert " + collection;
    }

    private String generateUpdate(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var filter = ctx.operation().string(this.elements, "filter");
        var update = ctx.operation().string(this.elements, "update");
        var upsert = ctx.operation().flag(this.elements, "upsert");
        var many = ctx.operation().flag(this.elements, "many");
        var collection = this.resolveCollection(ctx, null);

        this.rawCollection(b, collection);
        b.addStatement("var _filter = $L", BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver));
        b.addStatement("var _update = $L", BsonTemplate.parseDocument(update, ctx.method(), "update").toCodeBlock(resolver));
        ctx.parameters().validateAllUsed();
        b.addStatement("var _options = new $T().upsert($L)", MongoTypes.UPDATE_OPTIONS, upsert);

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        var operation = many ? "updateMany" : "updateOne";
        b.addStatement("var _result = _session == null ? _collection.$N(_filter, _update, _options) : _collection.$N(_session, _filter, _update, _options)", operation, operation);
        this.emitCountResult(b, ctx, CodeBlock.of("_result.getModifiedCount() + (_result.getUpsertedId() != null ? 1 : 0)"));
        this.closeTry(b);

        return "update " + collection + " " + filter + " " + update;
    }

    private String generateReplace(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var filter = ctx.operation().string(this.elements, "filter");
        var upsert = ctx.operation().flag(this.elements, "upsert");

        var filterCode = BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver);
        var parameter = ctx.parameters().entityParameter("@MongoReplace");
        ctx.parameters().validateAllUsed();
        var collection = this.resolveCollection(ctx, parameter.type());

        this.typedCollection(b, ctx, collection, parameter.type());
        b.addStatement("var _filter = $L", filterCode);
        b.addStatement("var _options = new $T().upsert($L)", MongoTypes.REPLACE_OPTIONS, upsert);

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        b.addStatement("var _result = _session == null ? _collection.replaceOne(_filter, $N, _options) : _collection.replaceOne(_session, _filter, $N, _options)",
            parameter.name(), parameter.name());
        this.emitCountResult(b, ctx, CodeBlock.of("_result.getModifiedCount() + (_result.getUpsertedId() != null ? 1 : 0)"));
        this.closeTry(b);

        return "replace " + collection + " " + filter;
    }

    private String generateDelete(CodeBlock.Builder b, Context ctx) {
        var resolver = ctx.parameters().resolver();
        var filter = ctx.operation().string(this.elements, "filter");
        var many = ctx.operation().flag(this.elements, "many");
        var collection = this.resolveCollection(ctx, null);

        this.rawCollection(b, collection);
        b.addStatement("var _filter = $L", BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver));
        ctx.parameters().validateAllUsed();

        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        var operation = many ? "deleteMany" : "deleteOne";
        b.addStatement("var _result = _session == null ? _collection.$N(_filter) : _collection.$N(_session, _filter)", operation, operation);
        this.emitCountResult(b, ctx, CodeBlock.of("_result.getDeletedCount()"));
        this.closeTry(b);

        return "delete " + collection + " " + filter;
    }

    /**
     * A numeric attribute is either an integer literal or a {@code :name} of an int parameter, which is what makes
     * runtime paging expressible.
     */
    @Nullable
    private CodeBlock intAttribute(Context ctx, @Nullable String value, String attribute) {
        if (value == null) {
            return null;
        }
        if (value.startsWith(":")) {
            return CodeBlock.of("$N", ctx.parameters().requireInt(value.substring(1), attribute).name());
        }
        try {
            var parsed = Integer.parseInt(value.trim());
            return parsed <= 0
                ? null
                : CodeBlock.of("$L", parsed);
        } catch (NumberFormatException e) {
            throw new ProcessingErrorException("""
                Mongo repository method is invalid:
                  %s#%s

                Problem:
                  Attribute '%s' is '%s', which is neither an integer nor a ':name' reference.

                Hint:
                  Write a literal such as 10, or ':size' to take the value from an int method parameter.

                Fix:
                  Correct the attribute value.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), attribute, value), ctx.method());
        }
    }

    private String generateUpdateBatch(CodeBlock.Builder b, Context ctx, MongoParameters.Parameter batch) {
        var elementType = this.batchElementType(ctx, batch);
        var filter = ctx.operation().string(this.elements, "filter");
        var update = ctx.operation().string(this.elements, "update");
        var upsert = ctx.operation().flag(this.elements, "upsert");
        var many = ctx.operation().flag(this.elements, "many");
        var collection = this.resolveCollection(ctx, elementType);

        ctx.parameters().bindBatchElement(batch, "_b", elementType);
        var resolver = ctx.parameters().resolver();
        var filterCode = BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver);
        var updateCode = BsonTemplate.parseDocument(update, ctx.method(), "update").toCodeBlock(resolver);
        ctx.parameters().validateAllUsed();

        this.rawCollection(b, collection);
        b.addStatement("var _options = new $T().upsert($L)", MongoTypes.UPDATE_OPTIONS, upsert);
        this.emitModels(b, ctx, batch, MongoTypes.DOCUMENT,
            CodeBlock.of("new $T<>($L, $L, _options)", many ? MongoTypes.UPDATE_MANY_MODEL : MongoTypes.UPDATE_ONE_MODEL, filterCode, updateCode));
        this.emitBulkWrite(b, ctx, CodeBlock.of("_result.getModifiedCount() + _result.getUpserts().size()"));

        return "bulk update " + collection + " " + filter + " " + update;
    }

    private String generateReplaceBatch(CodeBlock.Builder b, Context ctx, MongoParameters.Parameter batch) {
        var elementType = this.batchElementType(ctx, batch);
        var filter = ctx.operation().string(this.elements, "filter");
        var upsert = ctx.operation().flag(this.elements, "upsert");
        var collection = this.resolveCollection(ctx, elementType);

        ctx.parameters().bindBatchElement(batch, "_b", elementType);
        var filterCode = BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(ctx.parameters().resolver());
        ctx.parameters().validateAllUsed();

        this.typedCollection(b, ctx, collection, elementType);
        b.addStatement("var _options = new $T().upsert($L)", MongoTypes.REPLACE_OPTIONS, upsert);
        this.emitModels(b, ctx, batch, TypeName.get(elementType),
            CodeBlock.of("new $T<>($L, _b, _options)", MongoTypes.REPLACE_ONE_MODEL, filterCode));
        this.emitBulkWrite(b, ctx, CodeBlock.of("_result.getModifiedCount() + _result.getUpserts().size()"));

        return "bulk replace " + collection + " " + filter;
    }

    private String generateDeleteBatch(CodeBlock.Builder b, Context ctx, MongoParameters.Parameter batch) {
        var elementType = this.batchElementType(ctx, batch);
        var filter = ctx.operation().string(this.elements, "filter");
        var many = ctx.operation().flag(this.elements, "many");
        var collection = this.resolveCollection(ctx, elementType);

        ctx.parameters().bindBatchElement(batch, "_b", elementType);
        var filterCode = BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(ctx.parameters().resolver());
        ctx.parameters().validateAllUsed();

        this.rawCollection(b, collection);
        this.emitModels(b, ctx, batch, MongoTypes.DOCUMENT,
            CodeBlock.of("new $T<>($L)", many ? MongoTypes.DELETE_MANY_MODEL : MongoTypes.DELETE_ONE_MODEL, filterCode));
        this.emitBulkWrite(b, ctx, CodeBlock.of("(long) _result.getDeletedCount()"));

        return "bulk delete " + collection + " " + filter;
    }

    private void emitModels(CodeBlock.Builder b, Context ctx, MongoParameters.Parameter batch, TypeName documentType, CodeBlock model) {
        b.addStatement("var _models = new $T<$T<$T>>($N.size())", ArrayList.class, MongoTypes.WRITE_MODEL, documentType, batch.name());
        b.beginControlFlow("for (var _b : $N)", batch.name());
        b.addStatement("_models.add($L)", model);
        b.endControlFlow();
    }

    /**
     * An empty batch is not sent: the driver rejects a bulkWrite with no models.
     */
    private void emitBulkWrite(CodeBlock.Builder b, Context ctx, CodeBlock affected) {
        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        b.addStatement("long _affected = 0");
        b.beginControlFlow("if (!_models.isEmpty())");
        b.addStatement("var _result = _session == null ? _collection.bulkWrite(_models) : _collection.bulkWrite(_session, _models)");
        b.addStatement("_affected = $L", affected);
        b.endControlFlow();
        this.emitCountResult(b, ctx, CodeBlock.of("_affected"));
        this.closeTry(b);
    }

    private TypeMirror batchElementType(Context ctx, MongoParameters.Parameter batch) {
        var elementType = this.collectionElementType(batch.type());
        if (elementType == null) {
            throw new ProcessingErrorException("""
                Mongo repository method is invalid:
                  %s#%s

                Problem:
                  Parameter '%s' is annotated with @Batch but is not a List, Set or Collection.

                Hint:
                  A batch operation walks a collection and turns every element into one bulkWrite model.

                Fix:
                  Declare the parameter as a collection, or remove @Batch.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), batch.name()), ctx.method());
        }
        return elementType;
    }

    private void typedCollection(CodeBlock.Builder b, Context ctx, String collection, TypeMirror entityType) {
        var codecField = ctx.codecs().add(ParameterizedTypeName.get(MongoTypes.CODEC, TypeName.get(entityType).box()), (String) null);
        var registry = ctx.registries().forCodec(codecField);
        b.addStatement("var _collection = this.$N.database().getCollection($S, $T.class).withCodecRegistry(this.$N)",
            EXECUTOR, collection, TypeName.get(entityType), registry);
    }

    private void rawCollection(CodeBlock.Builder b, String collection) {
        b.addStatement("var _collection = this.$N.database().getCollection($S)", EXECUTOR, collection);
    }

    private void session(CodeBlock.Builder b) {
        b.addStatement("var _session = this.$N.currentSession()", EXECUTOR);
    }

    private void closeTry(CodeBlock.Builder b) {
        b.nextControlFlow("catch (Exception _e)")
            .addStatement("_observation.observeError(_e)")
            .addStatement("throw _e")
            .nextControlFlow("finally")
            .addStatement("_observation.end()")
            .endControlFlow();
    }

    private void emitIterableResult(CodeBlock.Builder b, Context ctx, TypeMirror entityType) {
        var returnType = ctx.methodType().getReturnType();
        if (this.isErasedTo(returnType, Optional.class)) {
            b.addStatement("return $T.ofNullable(_iterable.first())", Optional.class);
        } else if (this.isErasedTo(returnType, List.class)) {
            b.addStatement("return _iterable.into(new $T<$T>())", ArrayList.class, TypeName.get(entityType));
        } else {
            b.addStatement("return _iterable.first()");
        }
    }

    private void emitCountResult(CodeBlock.Builder b, Context ctx, CodeBlock count) {
        var returnType = ctx.methodType().getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            return;
        }
        var typeName = TypeName.get(returnType);
        if (typeName.equals(DbUtils.UPDATE_COUNT)) {
            b.addStatement("return new $T($L)", DbUtils.UPDATE_COUNT, count);
        } else if (typeName.box().equals(TypeName.LONG.box())) {
            b.addStatement("return $L", count);
        } else if (typeName.box().equals(TypeName.INT.box())) {
            b.addStatement("return (int) ($L)", count);
        } else {
            throw new ProcessingErrorException("""
                Mongo repository method has an unsupported return type:
                  %s#%s returns %s

                Problem:
                  This operation reports how many documents it affected.

                Hint:
                  Supported return types are void, UpdateCount, long and int.

                Fix:
                  Change the return type to one of the supported ones.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), returnType), ctx.method());
        }
    }

    private InsertResult insertResult(Context ctx, TypeMirror entityType, @Nullable MongoEntity entity, boolean many) {
        var returnType = ctx.methodType().getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            return new InsertResult.Nothing();
        }

        var declared = many ? this.collectionElementType(returnType) : returnType;
        var isList = !many || this.isErasedTo(returnType, List.class);
        if (declared != null && isList) {
            if (TypeName.get(declared).equals(MongoTypes.OBJECT_ID)) {
                this.requireObjectIdOrNoId(ctx, entity);
                return new InsertResult.Id();
            }
            if (this.types.isSameType(this.types.erasure(declared), this.types.erasure(entityType))) {
                return this.requireIdFieldOfObjectId(ctx, entityType, entity);
            }
        }

        throw new ProcessingErrorException("""
            Mongo repository method has an unsupported return type:
              %s#%s returns %s

            Problem:
              @MongoInsert can report the identifiers it wrote, or nothing at all.

            Hint:
              Supported return types are void, ObjectId and the entity type; for a collection parameter,
              List<ObjectId> and List<Entity>.

            Fix:
              Change the return type to one of the supported ones.
            """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), returnType), ctx.method());
    }

    private void requireObjectIdOrNoId(Context ctx, @Nullable MongoEntity entity) {
        if (entity == null) {
            return;
        }
        var id = entity.idField();
        if (id == null) {
            return;
        }
        if (!TypeName.get(id.type()).box().equals(MongoTypes.OBJECT_ID)) {
            throw new ProcessingErrorException("""
                Mongo repository method has an unsupported return type:
                  %s#%s returns %s

                Problem:
                  Entity %s maps '_id' to a %s, so the inserted identifier is not an ObjectId.

                Hint:
                  A server-generated identifier is always an ObjectId; a hand-assigned one keeps the type of the field.

                Fix:
                  Declare the method void, or make the '_id' field an ObjectId.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), ctx.methodType().getReturnType(),
                entity.typeElement().getQualifiedName(), id.type()), ctx.method());
        }
    }

    private InsertResult.Entity requireIdFieldOfObjectId(Context ctx, TypeMirror entityType, @Nullable MongoEntity entity) {
        var idField = entity == null ? null : entity.idField();
        if (entity == null || idField == null) {
            throw new ProcessingErrorException("""
                Mongo repository method has an unsupported return type:
                  %s#%s returns %s

                Problem:
                  Entity %s has no field mapped to '_id', so there is nowhere to write the generated identifier.

                Hint:
                  An entity result differs from its argument only by the identifier.

                Fix:
                  Add an @Id ObjectId field, or declare the method void or ObjectId.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), ctx.methodType().getReturnType(),
                entity == null ? entityType : entity.typeElement().getQualifiedName()), ctx.method());
        }
        this.requireObjectIdOrNoId(ctx, entity);
        return new InsertResult.Entity(entity, idField);
    }

    private void emitInsertOne(CodeBlock.Builder b, MongoParameters.Parameter parameter, InsertResult kind) {
        switch (kind) {
            case InsertResult.Nothing() -> b.beginControlFlow("if (_session == null)")
                .addStatement("_collection.insertOne($N)", parameter.name())
                .nextControlFlow("else")
                .addStatement("_collection.insertOne(_session, $N)", parameter.name())
                .endControlFlow();
            case InsertResult.Id() -> {
                this.emitInsertOneResult(b, parameter);
                b.addStatement("return _id");
            }
            case InsertResult.Entity(var entity, var idField) -> {
                this.emitInsertOneResult(b, parameter);
                if (entity.kind() == MongoEntity.EntityKind.RECORD) {
                    b.addStatement("return $L", entity.rebuildWithId(CodeBlock.of("_id"), CodeBlock.of("$N", parameter.name())));
                } else {
                    b.addStatement("$N.$N(_id)", parameter.name(), entity.setterName(idField));
                    b.addStatement("return $N", parameter.name());
                }
            }
        }
    }

    private void emitInsertOneResult(CodeBlock.Builder b, MongoParameters.Parameter parameter) {
        b.addStatement("var _result = _session == null ? _collection.insertOne($N) : _collection.insertOne(_session, $N)",
            parameter.name(), parameter.name());
        b.addStatement("var _id = $T.insertedId(_result)", MongoTypes.RESULTS);
    }

    private void emitInsertMany(CodeBlock.Builder b, MongoParameters.Parameter parameter, TypeMirror entityType, InsertResult kind) {
        b.addStatement("var _documents = $T.copyOf($N)", List.class, parameter.name());
        b.beginControlFlow("if (_documents.isEmpty())");
        if (kind instanceof InsertResult.Nothing) {
            b.addStatement("return");
        } else {
            b.addStatement("return $T.of()", List.class);
        }
        b.endControlFlow();

        switch (kind) {
            case InsertResult.Nothing() -> b.beginControlFlow("if (_session == null)")
                .addStatement("_collection.insertMany(_documents)")
                .nextControlFlow("else")
                .addStatement("_collection.insertMany(_session, _documents)")
                .endControlFlow();
            case InsertResult.Id() -> {
                this.emitInsertManyResult(b);
                b.addStatement("return _ids");
            }
            case InsertResult.Entity(var entity, var idField) -> {
                this.emitInsertManyResult(b);
                if (entity.kind() == MongoEntity.EntityKind.RECORD) {
                    b.addStatement("var _entities = new $T<$T>(_documents.size())", ArrayList.class, TypeName.get(entityType));
                    b.beginControlFlow("for (int _i = 0; _i < _documents.size(); _i++)");
                    b.addStatement("_entities.add($L)", entity.rebuildWithId(CodeBlock.of("_ids.get(_i)"), CodeBlock.of("_documents.get(_i)")));
                    b.endControlFlow();
                    b.addStatement("return _entities");
                } else {
                    b.beginControlFlow("for (int _i = 0; _i < _documents.size(); _i++)");
                    b.addStatement("_documents.get(_i).$N(_ids.get(_i))", entity.setterName(idField));
                    b.endControlFlow();
                    b.addStatement("return _documents");
                }
            }
        }
    }

    private void emitInsertManyResult(CodeBlock.Builder b) {
        b.addStatement("var _result = _session == null ? _collection.insertMany(_documents) : _collection.insertMany(_session, _documents)");
        b.addStatement("var _ids = $T.insertedIds(_result, _documents.size())", MongoTypes.RESULTS);
    }

    private TypeMirror resultEntityType(Context ctx) {
        var returnType = ctx.methodType().getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            throw new ProcessingErrorException("""
                Mongo repository method has an unsupported return type:
                  %s#%s returns void

                Problem:
                  A read operation must return the documents it reads.

                Hint:
                  Supported return types are an entity, Optional<Entity> and List<Entity>.

                Fix:
                  Declare a return type for the method.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName()), ctx.method());
        }
        if ((this.isErasedTo(returnType, Optional.class) || this.isErasedTo(returnType, List.class))
            && returnType instanceof DeclaredType declaredType
            && declaredType.getTypeArguments().size() == 1) {
            return declaredType.getTypeArguments().get(0);
        }
        return returnType;
    }

    private String resolveCollection(Context ctx, @Nullable TypeMirror entityType) {
        var explicit = ctx.operation().stringOrNull(this.elements, "collection");
        if (explicit != null) {
            return explicit;
        }
        var onRepository = AnnotationUtils.findAnnotation(ctx.repository(), MongoTypes.MONGO_COLLECTION);
        if (onRepository != null) {
            return AnnotationUtils.parseAnnotationValueWithoutDefault(onRepository, "value");
        }
        if (entityType != null && this.types.asElement(entityType) instanceof TypeElement entityElement) {
            var onEntity = AnnotationUtils.findAnnotation(entityElement, MongoTypes.MONGO_COLLECTION);
            if (onEntity != null) {
                return AnnotationUtils.parseAnnotationValueWithoutDefault(onEntity, "value");
            }
        }
        throw new ProcessingErrorException("""
            Mongo collection can not be resolved:
              %s#%s

            Problem:
              The operation does not say which collection it works with.

            Hint:
              A collection is taken from the 'collection' attribute of the operation, then from @MongoCollection on the
              repository, then from @MongoCollection on the entity.

            Fix:
              Set the 'collection' attribute, or annotate the repository or the entity with @MongoCollection.
            """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName()), ctx.method());
    }

    @Nullable
    private TypeMirror collectionElementType(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) {
            return null;
        }
        var erasure = this.types.erasure(type).toString();
        if (!erasure.equals(List.class.getCanonicalName())
            && !erasure.equals(Set.class.getCanonicalName())
            && !erasure.equals(java.util.Collection.class.getCanonicalName())) {
            return null;
        }
        var arguments = declaredType.getTypeArguments();
        return arguments.size() == 1
            ? arguments.get(0)
            : null;
    }

    private boolean isErasedTo(TypeMirror type, Class<?> expected) {
        return type instanceof DeclaredType
            && this.types.erasure(type).toString().equals(expected.getCanonicalName());
    }
}

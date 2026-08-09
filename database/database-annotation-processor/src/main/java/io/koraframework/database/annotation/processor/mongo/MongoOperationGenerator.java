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
        var limit = ctx.operation().number(this.elements, "limit");
        var skip = ctx.operation().number(this.elements, "skip");

        var entityType = this.resultEntityType(ctx);
        var collection = this.resolveCollection(ctx, entityType);

        this.typedCollection(b, ctx, collection, entityType);
        b.addStatement("var _filter = $L", BsonTemplate.parseDocument(filter, ctx.method(), "filter").toCodeBlock(resolver));
        var projectionCode = projection == null ? null : BsonTemplate.parseDocument(projection, ctx.method(), "projection").toCodeBlock(resolver);
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
        if (skip > 0) {
            b.addStatement("_iterable = _iterable.skip($L)", skip);
        }
        if (limit > 0) {
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

    private String generateInsert(CodeBlock.Builder b, Context ctx) {
        this.requireVoid(ctx, "@MongoInsert");
        var parameter = ctx.parameters().entityParameter("@MongoInsert");
        ctx.parameters().validateAllUsed();

        var elementType = this.collectionElementType(parameter.type());
        var entityType = elementType == null ? parameter.type() : elementType;
        var collection = this.resolveCollection(ctx, entityType);

        this.typedCollection(b, ctx, collection, entityType);
        b.addStatement("_observation.observeStatement()");
        b.beginControlFlow("try");
        this.session(b);
        if (elementType == null) {
            b.beginControlFlow("if (_session == null)")
                .addStatement("_collection.insertOne($N)", parameter.name())
                .nextControlFlow("else")
                .addStatement("_collection.insertOne(_session, $N)", parameter.name())
                .endControlFlow();
        } else {
            b.addStatement("var _documents = $T.copyOf($N)", List.class, parameter.name());
            b.beginControlFlow("if (_session == null)")
                .addStatement("_collection.insertMany(_documents)")
                .nextControlFlow("else")
                .addStatement("_collection.insertMany(_session, _documents)")
                .endControlFlow();
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

    private void requireVoid(Context ctx, String operation) {
        if (ctx.methodType().getReturnType().getKind() != TypeKind.VOID) {
            throw new ProcessingErrorException("""
                Mongo repository method has an unsupported return type:
                  %s#%s returns %s

                Problem:
                  %s does not produce a result.

                Hint:
                  The driver reports nothing useful beyond the generated identifiers, which are written back into the document.

                Fix:
                  Declare the method as void.
                """.formatted(ctx.repository().getSimpleName(), ctx.method().getSimpleName(), ctx.methodType().getReturnType(), operation), ctx.method());
        }
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

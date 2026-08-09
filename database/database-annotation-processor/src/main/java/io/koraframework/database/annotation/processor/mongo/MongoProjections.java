package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.LinkedHashSet;

final class MongoProjections {

    private MongoProjections() {}

    /**
     * A projection is derived only for an @EntityMongo type: only then does Kora own the codec and know that the
     * field list is exactly what will be read back.
     */
    @Nullable
    static CodeBlock derive(Types types, TypeMirror entityType) {
        var element = types.asElement(entityType);
        if (element == null || AnnotationUtils.findAnnotation(element, MongoTypes.MONGO_ENTITY) == null) {
            return null;
        }
        var entity = MongoEntity.parse(types, entityType);
        if (entity == null) {
            return null;
        }
        // A dot in a field name is a literal character (e.g. @Column("addr.city")), but a dot in a projection
        // means a path into a subdocument. We cannot tell the two apart, so we leave the query alone.
        for (var field : entity.fields()) {
            if (field.bsonName().indexOf('.') >= 0) {
                return null;
            }
        }

        var b = CodeBlock.builder().add("new $T()", MongoTypes.BSON_DOCUMENT);
        for (var field : entity.fields()) {
            b.add(".append($S, new $T(1))", field.bsonName(), MongoTypes.BSON_INT32);
        }
        if (entity.idField() == null) {
            b.add(".append($S, new $T(0))", "_id", MongoTypes.BSON_INT32);
        }
        return b.build();
    }

    /**
     * Checks a hand-written projection against the result type, so a field missing from an inclusion projection (or
     * dropped by an exclusion projection) fails the build instead of surfacing as a null deep inside the codec.
     * Every case this can not read with certainty is skipped rather than guessed: a placeholder, an operator
     * expression, a non-{@code @EntityMongo} result type, or a dotted field name all leave the query alone.
     */
    static void validate(Types types, ExecutableElement method, TypeElement repository, TypeMirror entityType, BsonTemplate template) {
        if (!template.parameters().isEmpty()) {
            return;
        }
        var element = types.asElement(entityType);
        if (element == null || AnnotationUtils.findAnnotation(element, MongoTypes.MONGO_ENTITY) == null) {
            return;
        }
        var entity = MongoEntity.parse(types, entityType);
        if (entity == null) {
            return;
        }
        for (var field : entity.fields()) {
            if (field.bsonName().indexOf('.') >= 0) {
                return;
            }
        }

        var document = template.document();
        var included = new LinkedHashSet<String>();
        var excluded = new LinkedHashSet<String>();
        for (var entry : document.entrySet()) {
            var value = entry.getValue();
            if (value.isDocument()) {
                return;
            }
            if (isTruthy(value)) {
                included.add(entry.getKey());
            } else if (isFalsy(value)) {
                excluded.add(entry.getKey());
            } else {
                return;
            }
        }

        var includedWithoutId = included.stream().filter(name -> !name.equals("_id")).count();
        var excludedWithoutId = excluded.stream().filter(name -> !name.equals("_id")).count();
        if (includedWithoutId > 0 && excludedWithoutId > 0) {
            throw new ProcessingErrorException("""
                Mongo projection is invalid:
                  %s#%s

                Problem:
                  The projection mixes included and excluded fields.

                Hint:
                  MongoDB accepts either an inclusion or an exclusion projection; only '_id' may be excluded from an
                  inclusion projection.

                Fix:
                  Keep either inclusions or exclusions.
                """.formatted(repository.getSimpleName(), method.getSimpleName()), method);
        }

        for (var field : entity.fields()) {
            if (field.nullable()) {
                continue;
            }
            var name = field.bsonName();
            var covered = includedWithoutId > 0
                ? included.stream().anyMatch(key -> key.equals(name) || key.startsWith(name + ".")) || (name.equals("_id") && !excluded.contains("_id"))
                : excluded.stream().noneMatch(key -> key.equals(name));
            if (!covered) {
                throw new ProcessingErrorException("""
                    Mongo projection does not cover the result type:
                      %s#%s

                    Problem:
                      %s.%s is not nullable, but field '%s' is not included in the projection.

                    Hint:
                      An inclusion projection returns only the listed fields, so every required field of the result type
                      must be listed.

                    Fix:
                      Add '%s' to the projection, make the field nullable, or drop the projection attribute and let Kora
                      derive it from the result type.
                    """.formatted(repository.getSimpleName(), method.getSimpleName(),
                    entity.typeElement().getSimpleName(), field.element().getSimpleName(), name, name), method);
            }
        }
    }

    private static boolean isTruthy(BsonValue value) {
        return (value.isNumber() && value.asNumber().intValue() != 0) || (value.isBoolean() && value.asBoolean().getValue());
    }

    private static boolean isFalsy(BsonValue value) {
        return (value.isNumber() && value.asNumber().intValue() == 0) || (value.isBoolean() && !value.asBoolean().getValue());
    }
}

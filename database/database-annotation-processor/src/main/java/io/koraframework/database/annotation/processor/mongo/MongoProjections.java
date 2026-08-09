package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import org.jspecify.annotations.Nullable;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

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
}

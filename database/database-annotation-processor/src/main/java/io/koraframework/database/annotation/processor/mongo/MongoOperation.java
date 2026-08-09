package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.stream.Collectors;

public record MongoOperation(Kind kind, AnnotationMirror annotation) {

    public enum Kind {
        FIND(MongoTypes.FIND),
        INSERT(MongoTypes.INSERT),
        UPDATE(MongoTypes.UPDATE),
        REPLACE(MongoTypes.REPLACE),
        DELETE(MongoTypes.DELETE),
        COUNT(MongoTypes.COUNT),
        AGGREGATE(MongoTypes.AGGREGATE);

        private final ClassName annotationName;

        Kind(ClassName annotationName) {
            this.annotationName = annotationName;
        }

        public ClassName annotationName() {
            return this.annotationName;
        }
    }

    public static MongoOperation parse(ExecutableElement method) {
        var found = new ArrayList<MongoOperation>();
        for (var kind : Kind.values()) {
            var annotation = AnnotationUtils.findAnnotation(method, kind.annotationName());
            if (annotation != null) {
                found.add(new MongoOperation(kind, annotation));
            }
        }

        if (found.isEmpty()) {
            throw new ProcessingErrorException("""
                Mongo repository method is invalid:
                  %s#%s

                Problem:
                  Abstract repository method has no Mongo operation annotation.

                Hint:
                  Kora generates a method body from one of @MongoFind, @MongoInsert, @MongoUpdate, @MongoReplace,
                  @MongoDelete, @MongoCount or @MongoAggregate.

                Fix:
                  Annotate the method with an operation annotation, or give it a default implementation.
                """.formatted(method.getEnclosingElement().getSimpleName(), method.getSimpleName()), method);
        }
        if (found.size() > 1) {
            throw new ProcessingErrorException("""
                Mongo repository method is invalid:
                  %s#%s

                Problem:
                  Method has more than one Mongo operation annotation: %s

                Hint:
                  A repository method maps to exactly one MongoDB operation.

                Fix:
                  Keep a single operation annotation on the method.
                """.formatted(method.getEnclosingElement().getSimpleName(), method.getSimpleName(),
                found.stream().map(o -> "@" + o.kind().annotationName().simpleName()).collect(Collectors.joining(", "))), method);
        }
        return found.get(0);
    }

    public String string(Elements elements, String attribute) {
        return AnnotationUtils.parseAnnotationValue(elements, this.annotation, attribute);
    }

    @Nullable
    public String stringOrNull(Elements elements, String attribute) {
        String value = AnnotationUtils.parseAnnotationValue(elements, this.annotation, attribute);
        return value == null || value.isBlank()
            ? null
            : value;
    }

    public boolean flag(Elements elements, String attribute) {
        return Boolean.TRUE.equals(AnnotationUtils.<Boolean>parseAnnotationValue(elements, this.annotation, attribute));
    }

    public int number(Elements elements, String attribute) {
        Integer value = AnnotationUtils.parseAnnotationValue(elements, this.annotation, attribute);
        return value == null ? 0 : value;
    }
}

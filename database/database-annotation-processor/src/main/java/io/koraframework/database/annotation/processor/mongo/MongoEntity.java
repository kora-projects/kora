package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.RecordUtils;
import io.koraframework.database.annotation.processor.DbUtils;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mongo entity model. Unlike {@code DbEntity} it keeps nested objects nested: a nested document is mapped by its own
 * codec instead of being flattened into prefixed columns the way SQL modules do.
 */
public final class MongoEntity {

    public enum EntityKind {
        RECORD, BEAN
    }

    public record Field(VariableElement element, TypeMirror type, String bsonName, boolean nullable, String accessor) {

        public String variableName() {
            return "_" + this.element.getSimpleName().toString();
        }
    }

    private final TypeMirror typeMirror;
    private final TypeElement typeElement;
    private final EntityKind kind;
    private final List<Field> fields;

    private MongoEntity(TypeMirror typeMirror, TypeElement typeElement, EntityKind kind, List<Field> fields) {
        this.typeMirror = typeMirror;
        this.typeElement = typeElement;
        this.kind = kind;
        this.fields = fields;
    }

    public TypeMirror typeMirror() {
        return this.typeMirror;
    }

    public TypeElement typeElement() {
        return this.typeElement;
    }

    public List<Field> fields() {
        return this.fields;
    }

    @Nullable
    public Field idField() {
        for (var field : this.fields) {
            if (field.bsonName().equals("_id")) {
                return field;
            }
        }
        return null;
    }

    public CodeBlock rebuildWithId(CodeBlock idExpr, CodeBlock sourceExpr) {
        var id = this.idField();
        var b = CodeBlock.builder().add("new $T(", TypeName.get(this.typeMirror));
        for (int i = 0; i < this.fields.size(); i++) {
            if (i > 0) {
                b.add(", ");
            }
            var field = this.fields.get(i);
            b.add(field == id ? idExpr : CodeBlock.of("$L.$N()", sourceExpr, field.accessor()));
        }
        return b.add(")").build();
    }

    public String setterName(Field field) {
        return "set" + CommonUtils.capitalize(field.element().getSimpleName().toString());
    }

    public EntityKind kind() {
        return this.kind;
    }

    public CodeBlock buildInstance(String variableName) {
        var b = CodeBlock.builder();
        switch (this.kind) {
            case RECORD -> {
                b.add("$[var $N = new $T(", variableName, TypeName.get(this.typeMirror)).indent().add("\n");
                for (int i = 0; i < this.fields.size(); i++) {
                    if (i > 0) {
                        b.add(",\n");
                    }
                    b.add("$N", this.fields.get(i).variableName());
                }
                b.unindent().add("\n);$]\n");
            }
            case BEAN -> {
                b.addStatement("var $N = new $T()", variableName, TypeName.get(this.typeMirror));
                for (var field : this.fields) {
                    var setter = "set" + CommonUtils.capitalize(field.element().getSimpleName().toString());
                    b.addStatement("$N.$N($N)", variableName, setter, field.variableName());
                }
            }
        }
        return b.build();
    }

    @Nullable
    public static MongoEntity parse(Types types, TypeMirror typeMirror) {
        var typeElement = (TypeElement) types.asElement(typeMirror);
        if (typeElement == null) {
            return null;
        }
        var entity = isRecord(typeElement)
            ? parseRecord(typeElement)
            : parseJavaBean(types, typeMirror, typeElement);
        if (entity != null) {
            validate(entity);
        }
        return entity;
    }

    private static MongoEntity parseRecord(TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var fields = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(MongoEntity::isNotStaticField)
            .map(VariableElement.class::cast)
            .map(fieldElement -> {
                rejectEmbedded(fieldElement);
                return new Field(
                    fieldElement,
                    fieldElement.asType(),
                    parseBsonName(fieldElement, nameConverter),
                    isNullableRecordField(fieldElement, typeElement),
                    fieldElement.getSimpleName().toString()
                );
            })
            .toList();
        return new MongoEntity(typeElement.asType(), typeElement, EntityKind.RECORD, fields);
    }

    @Nullable
    private static MongoEntity parseJavaBean(Types types, TypeMirror typeMirror, TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var methods = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), Function.identity(), (e1, e2) -> e1));

        var fields = new ArrayList<Field>();
        for (var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD || !isNotStaticField(enclosed)) {
                continue;
            }
            var fieldElement = (VariableElement) enclosed;
            var fieldName = fieldElement.getSimpleName().toString();
            var fieldType = fieldElement.asType();
            var getter = methods.get("get" + CommonUtils.capitalize(fieldName));
            var setter = methods.get("set" + CommonUtils.capitalize(fieldName));
            if (getter == null || setter == null
                || !getter.getParameters().isEmpty()
                || setter.getParameters().size() != 1
                || setter.getReturnType().getKind() != TypeKind.VOID
                || !types.isSameType(getter.getReturnType(), fieldType)
                || !types.isSameType(setter.getParameters().get(0).asType(), fieldType)) {
                continue;
            }
            rejectEmbedded(fieldElement);
            fields.add(new Field(
                fieldElement,
                fieldType,
                parseBsonName(fieldElement, nameConverter),
                isNullableBeanField(fieldElement, setter),
                getter.getSimpleName().toString()
            ));
        }
        if (fields.isEmpty()) {
            return null;
        }
        return new MongoEntity(typeMirror, typeElement, EntityKind.BEAN, fields);
    }

    private static void validate(MongoEntity entity) {
        if (entity.fields.isEmpty()) {
            throw new ProcessingErrorException("""
                Mongo entity has no fields:
                  %s

                Problem:
                  @EntityMongo type has no persistable fields, so the generated codec would produce an empty document.

                Hint:
                  Kora maps record components, or bean fields that have both a getter and a setter.

                Fix:
                  Add fields to the entity, or remove @EntityMongo from this type.
                """.formatted(entity.typeElement.getQualifiedName()), entity.typeElement);
        }

        var seen = new HashMap<String, Field>();
        for (var field : entity.fields) {
            var previous = seen.put(field.bsonName(), field);
            if (previous != null) {
                throw new ProcessingErrorException("""
                    Mongo entity has duplicate document field:
                      %s#%s and %s#%s both map to '%s'

                    Problem:
                      Two entity fields map to the same BSON document field, so one of them would silently overwrite the other.

                    Hint:
                      A document field name comes from @Column, or from @Id which maps to '_id', or from the field name itself.

                    Fix:
                      Give one of the fields a distinct @Column name.
                    """.formatted(
                    entity.typeElement.getQualifiedName(), previous.element().getSimpleName(),
                    entity.typeElement.getQualifiedName(), field.element().getSimpleName(),
                    field.bsonName()), field.element());
            }
        }

        var id = entity.idField();
        if (id != null && id.nullable() && !TypeName.get(id.type()).box().equals(MongoTypes.OBJECT_ID)) {
            throw new ProcessingErrorException("""
                Mongo entity field is invalid:
                  %s.%s

                Problem:
                  Field mapped to '_id' is nullable but is not an ObjectId.

                Hint:
                  When '_id' is absent the server generates an ObjectId, which a %s field can not read back.

                Fix:
                  Declare the field as ObjectId, or make it non-nullable and assign the identifier yourself.
                """.formatted(entity.typeElement.getQualifiedName(), id.element().getSimpleName(), id.type()), id.element());
        }
    }

    private static void rejectEmbedded(VariableElement fieldElement) {
        if (AnnotationUtils.findAnnotation(fieldElement, DbUtils.EMBEDDED_ANNOTATION) != null) {
            throw new ProcessingErrorException("""
                Mongo entity field is invalid:
                  %s

                Problem:
                  @Embedded is not supported for Mongo entities.

                Hint:
                  @Embedded flattens a nested object into prefixed columns, which only makes sense for tabular databases.
                  MongoDB stores a nested object as a nested document instead.

                Fix:
                  Remove @Embedded and annotate the nested type with @EntityMongo, or supply a Codec for it.
                """.formatted(fieldElement.getSimpleName()), fieldElement);
        }
    }

    private static String parseBsonName(VariableElement element, CommonUtils.@Nullable NameConverter nameConverter) {
        var column = AnnotationUtils.findAnnotation(element, DbUtils.COLUMN_ANNOTATION);
        if (column != null) {
            return AnnotationUtils.parseAnnotationValueWithoutDefault(column, "value");
        }
        if (AnnotationUtils.isAnnotationPresent(element, DbUtils.ID_ANNOTATION)) {
            return "_id";
        }
        var fieldName = element.getSimpleName().toString();
        return nameConverter == null
            ? fieldName
            : nameConverter.convert(fieldName);
    }

    private static boolean isNullableRecordField(VariableElement field, TypeElement type) {
        if (CommonUtils.isNullable(field)) {
            return true;
        }
        var constructor = RecordUtils.findCanonicalConstructor(type);
        for (var param : constructor.getParameters()) {
            if (param.getSimpleName().contentEquals(field.getSimpleName())) {
                return CommonUtils.isNullable(param);
            }
        }
        throw new IllegalStateException("Kora internal error: record component has no matching canonical constructor parameter: " + type.getQualifiedName() + "." + field.getSimpleName());
    }

    private static boolean isNullableBeanField(VariableElement field, ExecutableElement setter) {
        if (CommonUtils.isNullable(field)) {
            return true;
        }
        return setter.getParameters().stream()
            .findFirst()
            .map(CommonUtils::isNullable)
            .orElse(false);
    }

    private static boolean isRecord(TypeElement typeElement) {
        var superclass = typeElement.getSuperclass();
        return superclass != null && superclass.toString().equals(Record.class.getCanonicalName());
    }

    private static boolean isNotStaticField(javax.lang.model.element.Element element) {
        return !element.getModifiers().contains(Modifier.STATIC);
    }
}

package ru.tinkoff.kora.database.annotation.processor.entity;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.EntityUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbEntity {
    private final TypeMirror typeMirror;
    private final TypeElement typeElement;
    private final DtoType entityType;
    private final List<EntityField> entityFields;
    private final List<Column> columns;

    public DbEntity(TypeMirror typeMirror, TypeElement typeElement, DtoType entityType, List<EntityField> entityFields) {
        this.typeMirror = typeMirror;
        this.typeElement = typeElement;
        this.entityType = entityType;
        this.entityFields = entityFields;
        this.columns = entityFields.stream()
            .<Column>flatMap(ef -> {
                if (ef instanceof SimpleEntityField simple) {
                    return Stream.of(new ColumnImpl(simple));
                } else {
                    var embedded = (EmbeddedEntityField) ef;
                    return embedded.fields()
                        .stream()
                        .map(ColumnImpl::new);
                }
            })
            .toList();
    }

    public List<Column> columns() {
        return this.columns;
    }

    public TypeMirror typeMirror() {
        return this.typeMirror;
    }

    public TypeElement typeElement() {
        return this.typeElement;
    }

    public CodeBlock buildInstance(String variableName) {
        return switch (entityType) {
            case RECORD -> {
                var b = CodeBlock.builder();
                b.add("$[var $N = new $T(", variableName, TypeName.get(this.typeMirror)).indent().add("\n");
                for (int i = 0; i < this.entityFields.size(); i++) {
                    var entityField = this.entityFields.get(i);
                    if (i > 0) {
                        b.add(",\n");
                    }
                    if (entityField instanceof SimpleEntityField simple) {
                        b.add("$N", simple.element().getSimpleName());
                    } else if (entityField instanceof EmbeddedEntityField embedded) {
                        b.add("$N", embedded.parent().element().getSimpleName());
                    }
                }
                yield b.unindent().add("\n);$]\n").build();
            }
            case BEAN -> {
                var b = CodeBlock.builder();
                b.addStatement("var $N = new $T()", variableName, TypeName.get(this.typeMirror));
                for (EntityField entityField : this.entityFields) {
                    if (entityField instanceof SimpleEntityField simple) {
                        var setter = "set" + CommonUtils.capitalize(simple.element().getSimpleName().toString());
                        b.addStatement("$N.$N($N)", variableName, setter, simple.element().getSimpleName());
                    } else if (entityField instanceof EmbeddedEntityField embedded) {
                        var setter = "set" + CommonUtils.capitalize(embedded.element().getSimpleName().toString());
                        b.addStatement("$N.$N($L)", variableName, setter, embedded.buildInstance());
                    }
                }
                yield b.build();
            }
        };
    }

    public CodeBlock buildEmbeddedFields() {
        var b = CodeBlock.builder();
        for (var entityField : this.entityFields) {
            if (entityField instanceof EmbeddedEntityField embedded) {
                b.addStatement("$T $N", embedded.typeMirror(), embedded.parent.element().getSimpleName());
                if (CommonUtils.isNullable(embedded.parent().element())) {
                    b.add("if (");
                    for (int j = 0; j < embedded.fields().size(); j++) {
                        var field = embedded.fields().get(j);
                        if (j > 0) {
                            b.add(" && ");
                        }
                        b.add("$N == null", field.variableName());
                    }
                    b.add(") {$>\n");
                    b.add("$N = null;$<\n} else {$>\n", embedded.parent().element().getSimpleName());
                    for (int j = 0; j < embedded.fields().size(); j++) {
                        var field = embedded.fields().get(j);
                        if (!CommonUtils.isNullable(field.element())) {
                            b.beginControlFlow("if ($N == null)", field.variableName())
                                .addStatement("throw new $T($S)", NullPointerException.class, "Field %s is not nullable, but column %s is null".formatted(field.element().getSimpleName(), field.columnName()))
                                .endControlFlow();
                        }
                    }
                }
                b.add("$N = $L;", embedded.parent().element().getSimpleName(), embedded.buildInstance());
                if (CommonUtils.isNullable(embedded.parent().element())) {
                    b.add("$<\n}\n");
                } else {
                    b.add(";\n");
                }
            }
        }

        return b.build();
    }

    public interface Column {
        VariableElement element();

        TypeMirror type();

        String queryParameterName(String variableName);

        String variableName();

        String columnName();

        String[] names();

        boolean isNullable();

        String accessor();
    }

    private record ColumnImpl(VariableElement element, TypeMirror type, String sqlParameterName, String variableName, String columnName, String[] names, boolean isNullable,
                              String accessor) implements Column {
        public ColumnImpl(SimpleEntityField simple) {
            this(
                simple.element,
                simple.typeMirror,
                simple.element.getSimpleName().toString(),
                simple.element.getSimpleName().toString(),
                simple.columnName(),
                new String[]{simple.element.getSimpleName().toString()},
                CommonUtils.isNullable(simple.element),
                simple.accessor());
        }

        public ColumnImpl(EmbeddedEntityField.Field f) {
            this(
                f.element(),
                f.typeMirror(),
                f.parent().element.getSimpleName() + "." + f.element.getSimpleName(),
                f.variableName(),
                f.columnName(),
                new String[]{f.parent.element.getSimpleName().toString(), f.element.getSimpleName().toString()},
                CommonUtils.isNullable(f.parent.element) || CommonUtils.isNullable(f.element),
                f.parent.accessor() + "()." + f.element.getSimpleName().toString()// todo
            );
        }

        @Override
        public String queryParameterName(String variableName) {
            return variableName + "." + this.sqlParameterName;
        }
    }


    private enum DtoType {
        RECORD, BEAN
    }

    private sealed interface EntityField {
        VariableElement element();

        TypeMirror typeMirror();
    }

    private record SimpleEntityField(VariableElement element, TypeMirror typeMirror, String columnName, DtoType entityType) implements EntityField {
        public String accessor() {
            return switch (entityType) {
                case RECORD -> this.element.getSimpleName().toString();
                case BEAN -> "get" + CommonUtils.capitalize(this.element.getSimpleName().toString());
            };
        }
    }

    private record EmbeddedEntityField(EntityField parent, TypeMirror typeMirror, VariableElement element, List<Field> fields) implements EntityField {
        public CodeBlock buildInstance() {
            var eb = CodeBlock.builder();
            eb.add("new $T(", TypeName.get(this.typeMirror())).indent().add("\n");
            for (int j = 0; j < this.fields().size(); j++) {
                var field = this.fields().get(j);
                if (j > 0) {
                    eb.add(",\n");
                }
                eb.add("$N", field.variableName());
            }
            eb.unindent().add("\n)");
            return eb.build();
        }

        private record Field(SimpleEntityField parent, VariableElement element, TypeMirror typeMirror, String columnName, DtoType entityType) {
            public String variableName() {
                return parent.element.getSimpleName() + "_" + element.getSimpleName().toString();
            }
        }
    }


    public static DbEntity parseEntity(Types types, TypeMirror typeMirror) {
        var typeElement = (TypeElement) types.asElement(typeMirror);
        if (typeElement == null) {
            return null;
        }
        if (isRecord(typeElement)) {
            return parseRecordEntity(types, typeElement);
        }
        var javaBeanEntity = parseJavaBean(types, typeMirror, typeElement);
        if (javaBeanEntity != null) {
            return javaBeanEntity;
        }
        return null;
    }

    private static DbEntity parseRecordEntity(Types types, TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var fields = typeElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(DbEntity::isNotStaticField)
            .<EntityField>map(e -> {
                var element = (VariableElement) e;
                var type = element.asType();
                var embedded = AnnotationUtils.findAnnotation(element, DbUtils.EMBEDDED_ANNOTATION);
                var columnName = EntityUtils.parseColumnName(element, nameConverter);
                var field = new SimpleEntityField(element, type, columnName, DtoType.RECORD);
                if (embedded == null) {
                    return field;
                }
                var prefix = Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(embedded, "value"), columnName + "_");
                var entity = parseEntity(types, type);
                var embeddedFields = new ArrayList<EmbeddedEntityField.Field>();
                for (var entityField : entity.entityFields) {
                    embeddedFields.add(new EmbeddedEntityField.Field(
                        field, entityField.element(), entityField.typeMirror(), prefix + ((SimpleEntityField) entityField).columnName(), DtoType.RECORD
                    ));
                }
                return new EmbeddedEntityField(
                    field, type, element, embeddedFields
                );
            })
            .toList();
        return new DbEntity(typeElement.asType(), typeElement, DtoType.RECORD, fields);
    }

    private static boolean isRecord(TypeElement typeElement) {
        var superclass = typeElement.getSuperclass();
        if (superclass == null) {
            return false;
        }
        return superclass.toString().equals(Record.class.getCanonicalName());
    }

    @Nullable
    private static DbEntity parseJavaBean(Types types, TypeMirror typeMirror, TypeElement typeElement) {
        var nameConverter = CommonUtils.getNameConverter(typeElement);
        var methods = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), Function.identity(), (e1, e2) -> e1));

        var fields = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(DbEntity::isNotStaticField)
            .map(VariableElement.class::cast)
            .<DbEntity.EntityField>mapMulti((field, sink) -> {
                var fieldType = field.asType();
                var fieldName = field.getSimpleName().toString();
                var getterName = "get" + CommonUtils.capitalize(fieldName);
                var setterName = "set" + CommonUtils.capitalize(fieldName);
                var getter = methods.get(getterName);
                var setter = methods.get(setterName);
                if (getter == null || setter == null) {
                    return;
                }
                if (!getter.getParameters().isEmpty()) {
                    return;
                }
                if (setter.getParameters().size() != 1 || setter.getReturnType().getKind() != TypeKind.VOID) {
                    return;
                }
                if (!types.isSameType(getter.getReturnType(), fieldType)) {
                    return;
                }
                if (!types.isSameType(setter.getParameters().get(0).asType(), fieldType)) {
                    return;
                }
                var columnName = EntityUtils.parseColumnName(field, nameConverter);
                sink.accept(new DbEntity.SimpleEntityField(field, fieldType, columnName, DtoType.BEAN));
            })
            .toList();
        if (fields.isEmpty()) {
            return null;
        }
        return new DbEntity(typeMirror, typeElement, DtoType.BEAN, fields);
    }

    private static boolean isNotStaticField(Element element) {
        return !element.getModifiers().contains(Modifier.STATIC);
    }

}

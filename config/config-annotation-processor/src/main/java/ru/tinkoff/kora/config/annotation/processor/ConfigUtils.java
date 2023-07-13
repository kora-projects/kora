package ru.tinkoff.kora.config.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils.MappingData;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.common.util.Either;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import java.util.*;

public class ConfigUtils {
    public static final Set<TypeName> SUPPORTED_TYPES = Set.of(
        TypeName.INT, TypeName.INT.box(),
        TypeName.LONG, TypeName.LONG.box(),
        TypeName.DOUBLE, TypeName.DOUBLE.box(),
        ClassName.get(String.class)
    );

    public static boolean isSupportedType(TypeName typeName) {
        return SUPPORTED_TYPES.contains(typeName);
    }

    public record ConfigField(String name, TypeName typeName, boolean isNullable, boolean hasDefault, @Nullable MappingData mapping) {}

    public static Either<List<ConfigField>, List<ProcessingError>> parseFields(Types types, TypeElement typeElement) {
        var errors = new ArrayList<ProcessingError>();
        var seen = new HashSet<String>();
        var fields = new ArrayList<ConfigField>();

        var visitor = new Object() {
            public void acceptRecord(DeclaredType typeMirror, TypeElement te) {
                if (te.getKind() != ElementKind.RECORD) {
                    throw new IllegalArgumentException("Method expecting record");
                }
                for (var recordComponent : te.getRecordComponents()) {
                    var recordComponentType = types.asMemberOf(typeMirror, recordComponent);
                    var name = recordComponent.getSimpleName().toString();
                    if (seen.add(name)) {
                        var mapping = CommonUtils.parseMapping(recordComponent).getMapping(ConfigClassNames.configValueExtractor);
                        var isNullable = CommonUtils.isNullable(recordComponent) && !recordComponentType.getKind().isPrimitive();
                        fields.add(new ConfigUtils.ConfigField(
                            name, TypeName.get(recordComponentType), isNullable, false, mapping
                        ));
                    }
                }
            }

            public void acceptClass(DeclaredType typeMirror, TypeElement te) {
                if (te.getKind() != ElementKind.CLASS) {
                    throw new IllegalArgumentException("Method expecting record");
                }
                if (te.getModifiers().contains(Modifier.ABSTRACT)) {
                    errors.add(new ProcessingError("Config annotated class can't be abstract", te));
                    return;
                }
                ExecutableElement equals = null;
                ExecutableElement hashCode = null;
                class FieldAndAccessors {
                    VariableElement field;
                    ExecutableElement getter;
                    ExecutableElement setter;
                }
                var fieldsWithAccessors = new HashMap<String, FieldAndAccessors>();
                for (var enclosedElement : te.getEnclosedElements()) {
                    var name = enclosedElement.getSimpleName().toString();
                    if (enclosedElement.getKind() == ElementKind.FIELD) {
                        fieldsWithAccessors.computeIfAbsent(name, n -> new FieldAndAccessors()).field = (VariableElement) enclosedElement;
                    }
                    if (enclosedElement.getKind() == ElementKind.METHOD) {
                        if (enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                            continue;
                        }
                        if (enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                            continue;
                        }
                        var method = (ExecutableElement) enclosedElement;
                        if (name.equals("equals") && method.getParameters().size() == 1) {
                            equals = method;
                        } else if (name.equals("hashCode") && method.getParameters().isEmpty()) {
                            hashCode = method;
                        } else {
                            if (name.startsWith("get")) {
                                fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).getter = method;
                            } else if (name.startsWith("set")) {
                                fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).setter = method;
                            }
                        }
                    }
                }
                if (equals == null || hashCode == null) {
                    errors.add(new ProcessingError("Config annotated class must override equals and hashCode methods", typeElement));
                    return;
                }

                for (var fieldWithAccessors : fieldsWithAccessors.entrySet()) {
                    var name = fieldWithAccessors.getKey();
                    var value = fieldWithAccessors.getValue();
                    if (value.setter == null || value.getter == null) {
                        continue;
                    }
                    var fieldType = types.asMemberOf(typeMirror, value.field);
                    if (seen.add(name)) {
                        var isNullable = CommonUtils.isNullable(value.field) && !fieldType.getKind().isPrimitive();
                        var mapping = CommonUtils.parseMapping(value.field).getMapping(ConfigClassNames.configValueExtractor);
                        fields.add(new ConfigUtils.ConfigField(
                            name, TypeName.get(fieldType), isNullable, true, mapping
                        ));
                    }
                }
            }

            public void acceptInterface(DeclaredType typeMirror, TypeElement te) {
                if (te.getKind() != ElementKind.INTERFACE) {
                    throw new IllegalArgumentException("Method expecting interface");
                }
                for (var enclosedElement : te.getEnclosedElements()) {
                    if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                        continue;
                    }
                    var method = (ExecutableElement) enclosedElement;
                    if (!method.getParameters().isEmpty()) {
                        if (method.getModifiers().contains(Modifier.DEFAULT)) {
                            continue;
                        } else {
                            errors.add(new ProcessingError("Config has non default method with arguments", method));
                            return;
                        }
                    }
                    if (method.getReturnType().getKind() == TypeKind.VOID) {
                        if (method.getModifiers().contains(Modifier.DEFAULT)) {
                            continue;
                        }
                        errors.add(new ProcessingError("Config has non default method returning void", method));
                        return;
                    }
                    if (!method.getTypeParameters().isEmpty()) {
                        errors.add(new ProcessingError("Config has method with type parameters", method));
                        return;
                    }
                    var methodType = (ExecutableType) types.asMemberOf(typeMirror, method);
                    var name = method.getSimpleName().toString();
                    if (seen.add(name)) {
                        var isNullable = CommonUtils.isNullable(method) && !methodType.getReturnType().getKind().isPrimitive();
                        var mapping = CommonUtils.parseMapping(method).getMapping(ConfigClassNames.configValueExtractor);
                        fields.add(new ConfigUtils.ConfigField(
                            name, TypeName.get(methodType.getReturnType()), isNullable, method.getModifiers().contains(Modifier.DEFAULT), mapping
                        ));
                    }
                    for (var superinterface : te.getInterfaces()) {
                        var superinterfaceElement = (TypeElement) types.asElement(superinterface);
                        this.acceptInterface((DeclaredType) superinterface, superinterfaceElement);
                    }
                }
            }
        };
        var type = (DeclaredType) typeElement.asType();
        if (typeElement.getKind() == ElementKind.RECORD) {
            visitor.acceptRecord(type, typeElement);
        } else if (typeElement.getKind() == ElementKind.INTERFACE) {
            visitor.acceptInterface(type, typeElement);
        } else if (typeElement.getKind() == ElementKind.CLASS) {
            visitor.acceptClass(type, typeElement);
        } else {
            return Either.right(List.of(new ProcessingError("typeElement should be interface, class or record, got " + typeElement.getKind(), typeElement)));
        }
        if (errors.isEmpty()) {
            return Either.left(fields);
        } else {
            return Either.right(errors);
        }
    }
}

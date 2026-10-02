package io.koraframework.openfeature.annotation.processor;

import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.CommonUtils.MappingData;
import io.koraframework.annotation.processor.common.Either;
import io.koraframework.annotation.processor.common.ProcessingError;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigUtils {

    private ConfigUtils() {}

    public record ConfigField(String name, TypeName typeName, boolean isNullable, boolean hasDefault, @Nullable MappingData mapping) {}

    public static Either<List<ConfigField>, List<ProcessingError>> parseFields(Types types, TypeElement typeElement) {
        var type = (DeclaredType) typeElement.asType();
        if (typeElement.getKind() == ElementKind.INTERFACE) {
            return parseInterface(types, type, typeElement);
        } else {
            return Either.right(List.of(new ProcessingError("typeElement should be interface, class or record, got " + typeElement.getKind(), typeElement)));
        }
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseInterface(Types types, DeclaredType typeMirror, TypeElement te) {
        if (te.getKind() != ElementKind.INTERFACE) {
            throw new IllegalArgumentException("Method expecting interface");
        }
        var seen = new HashSet<String>();
        var errors = new ArrayList<ProcessingError>();
        var fields = new ArrayList<ConfigField>();

        parseInterface(types, typeMirror, te, fields, errors, seen);
        if (errors.isEmpty()) {
            return Either.left(fields);
        } else {
            return Either.right(errors);
        }
    }

    private static void parseInterface(Types types, DeclaredType typeMirror, TypeElement te, List<ConfigField> fields, List<ProcessingError> errors, Set<String> seen) {
        if (te.getKind() != ElementKind.INTERFACE) {
            throw new IllegalArgumentException("Method expecting interface");
        }
        for (var enclosedElement : te.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            var method = (ExecutableElement) enclosedElement;
            if (!method.getParameters().isEmpty()) {
                if (method.getModifiers().contains(Modifier.DEFAULT) || "withContext".equals(method.getSimpleName().toString())) {
                    continue;
                } else {
                    errors.add(new ProcessingError("Config has non default method with arguments", method));
                }
            }
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                if (method.getModifiers().contains(Modifier.DEFAULT)) {
                    continue;
                }
                errors.add(new ProcessingError("Config has non default method returning void", method));
            }
            if (!method.getTypeParameters().isEmpty()) {
                errors.add(new ProcessingError("Config has method with type parameters", method));
            }
            var methodType = (ExecutableType) types.asMemberOf(typeMirror, method);
            var name = method.getSimpleName().toString();
            if (seen.add(name)) {
                var isNullable = CommonUtils.isNullable(method) && !methodType.getReturnType().getKind().isPrimitive();
                var mapping = CommonUtils.parseMapping(method).getMapping(ConfigClassNames.configValueExtractor);
                fields.add(new ConfigField(
                    name, TypeName.get(methodType.getReturnType()), isNullable, method.getModifiers().contains(Modifier.DEFAULT), mapping
                ));
            }
            for (var superinterface : te.getInterfaces()) {
                var superinterfaceElement = (TypeElement) types.asElement(superinterface);
                parseInterface(types, (DeclaredType) superinterface, superinterfaceElement, fields, errors, seen);
            }
        }
    }
}

package io.koraframework.kora.app.annotation.processor.exception;

import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats dependency sources for error messages without Element#toString() noise:
 * annotations are placed before the type, the requested parameter is printed with canonical names
 * and all other parameters with simple names.
 */
public final class DependencySourceFormatter {

    private DependencySourceFormatter() {}

    public static String requiredAt(ComponentDeclaration declaration, @Nullable Element claimSource) {
        var executable = findExecutable(claimSource);
        if (executable == null) {
            executable = findExecutable(declaration.source());
        }
        if (executable == null || !(executable.getEnclosingElement() instanceof TypeElement)) {
            return declaration.declarationString();
        }
        return signature(executable, claimSource);
    }

    /**
     * "Required at" section for a dependency claim of the component: its signature and the requested parameter.
     * Starts with blank line so it can be appended right after the previous section.
     */
    public static String requiredAtSection(ComponentDeclaration declaration, @Nullable Element claimSource) {
        var msg = "\n\nRequired at:\n  " + requiredAt(declaration, claimSource);
        if (claimSource instanceof VariableElement parameter) {
            msg += "\n  parameter: " + parameter(parameter);
        }
        return msg;
    }

    /**
     * Section describing where the erroneous element is: "Required at" with the parameter for a parameter,
     * "Declared at" with the signature for a method or constructor, empty otherwise.
     * Starts with blank line so it can be appended right after the previous section.
     */
    public static String locationSection(@Nullable Element source) {
        if (source instanceof VariableElement parameter && parameter.getEnclosingElement() instanceof ExecutableElement executable && executable.getEnclosingElement() instanceof TypeElement) {
            return "\n\nRequired at:\n  " + signature(executable, parameter) + "\n  parameter: " + parameter(parameter);
        }
        if (source instanceof ExecutableElement executable && executable.getEnclosingElement() instanceof TypeElement) {
            return "\n\nDeclared at:\n  " + signature(executable, null);
        }
        return "";
    }

    public static String signature(ExecutableElement executable, @Nullable Element requestedParameter) {
        var owner = (TypeElement) executable.getEnclosingElement();
        // signature is printed as section body indented by 2 spaces, so parameters go one level deeper
        var params = executable.getParameters().isEmpty()
            ? "()"
            : executable.getParameters().stream()
            .map(p -> parameterType(p, p.equals(requestedParameter)))
            .collect(Collectors.joining(",\n    ", "(\n    ", ")"));
        if (executable.getKind() == ElementKind.CONSTRUCTOR) {
            return owner.getQualifiedName() + params;
        } else {
            return owner.getQualifiedName() + "#" + executable.getSimpleName() + params;
        }
    }

    public static String parameter(VariableElement parameter) {
        return parameterType(parameter, true) + " " + parameter.getSimpleName();
    }

    /**
     * Found public constructors as "found: ..." line value, each constructor on its own line.
     */
    public static String constructors(List<ExecutableElement> constructors) {
        if (constructors.isEmpty()) {
            return "no public constructors";
        }
        return constructors.stream()
            .map(c -> "\n    - " + compactSignature(c))
            .collect(Collectors.joining("", constructors.size() + " public constructors:", ""));
    }

    /**
     * Single line signature with simple type names, for listing several executables.
     */
    public static String compactSignature(ExecutableElement executable) {
        var params = executable.getParameters().stream()
            .map(p -> parameterType(p, false))
            .collect(Collectors.joining(", ", "(", ")"));
        var owner = (TypeElement) executable.getEnclosingElement();
        if (executable.getKind() == ElementKind.CONSTRUCTOR) {
            return owner.getQualifiedName() + params;
        } else {
            return owner.getQualifiedName() + "#" + executable.getSimpleName() + params;
        }
    }

    /**
     * Tag as it is written in code: {@code @Pg} for tag annotations and {@code @Tag(Some.class)} for tag classes, with canonical names.
     */
    public static String tag(Elements elements, String tag) {
        var element = elements.getTypeElement(tag);
        if (element != null && element.getKind() == ElementKind.ANNOTATION_TYPE) {
            return "@" + tag;
        }
        return "@Tag(" + tag + ".class)";
    }

    /**
     * Tag suffix for a component or dependency type: " (no tags)" or " with @Tag(...)".
     */
    public static String tagSuffix(Elements elements, @Nullable String tag) {
        return tag == null ? " (no tags)" : " with " + tag(elements, tag);
    }

    /**
     * Type with canonical names and without type annotations noise.
     */
    public static String type(TypeMirror type) {
        return typeName(type, true);
    }

    @Nullable
    private static ExecutableElement findExecutable(@Nullable Element element) {
        while (element != null) {
            if (element instanceof ExecutableElement executable) {
                return executable;
            }
            if (element instanceof TypeElement) {
                return null;
            }
            element = element.getEnclosingElement();
        }
        return null;
    }

    private static String parameterType(VariableElement parameter, boolean canonical) {
        // annotations with PARAMETER and TYPE_USE targets are present both on element and on type
        var annotations = new LinkedHashMap<String, AnnotationMirror>();
        for (var a : parameter.getAnnotationMirrors()) {
            annotations.putIfAbsent(a.getAnnotationType().toString(), a);
        }
        for (var a : parameter.asType().getAnnotationMirrors()) {
            annotations.putIfAbsent(a.getAnnotationType().toString(), a);
        }
        return annotationsPrefix(List.copyOf(annotations.values())) + typeName(parameter.asType(), canonical);
    }

    private static String type(TypeMirror type, boolean canonical) {
        return annotationsPrefix(type.getAnnotationMirrors()) + typeName(type, canonical);
    }

    private static String typeName(TypeMirror type, boolean canonical) {
        return switch (type.getKind()) {
            case DECLARED -> {
                var declared = (DeclaredType) type;
                var name = className((TypeElement) declared.asElement(), canonical);
                if (declared.getTypeArguments().isEmpty()) {
                    yield name;
                }
                yield declared.getTypeArguments().stream()
                    .map(t -> type(t, canonical))
                    .collect(Collectors.joining(", ", name + "<", ">"));
            }
            case ARRAY -> type(((ArrayType) type).getComponentType(), canonical) + "[]";
            case WILDCARD -> {
                var wildcard = (WildcardType) type;
                if (wildcard.getExtendsBound() != null) {
                    yield "? extends " + type(wildcard.getExtendsBound(), canonical);
                } else if (wildcard.getSuperBound() != null) {
                    yield "? super " + type(wildcard.getSuperBound(), canonical);
                } else {
                    yield "?";
                }
            }
            case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID -> type.getKind().name().toLowerCase();
            default -> type.toString();
        };
    }

    private static String className(TypeElement element, boolean canonical) {
        var qualifiedName = element.getQualifiedName().toString();
        if (canonical) {
            return qualifiedName;
        }
        Element e = element;
        while (e != null && !(e instanceof PackageElement)) {
            e = e.getEnclosingElement();
        }
        if (e instanceof PackageElement pkg && !pkg.isUnnamed()) {
            return qualifiedName.substring(pkg.getQualifiedName().length() + 1);
        }
        return qualifiedName;
    }

    private static String annotationsPrefix(List<? extends AnnotationMirror> annotations) {
        var sb = new StringBuilder();
        for (var annotation : annotations) {
            sb.append(annotation(annotation)).append(' ');
        }
        return sb.toString();
    }

    private static String annotation(AnnotationMirror annotation) {
        var name = "@" + className((TypeElement) annotation.getAnnotationType().asElement(), false);
        var values = annotation.getElementValues();
        if (values.isEmpty()) {
            return name;
        }
        if (values.size() == 1) {
            var entry = values.entrySet().iterator().next();
            if (entry.getKey().getSimpleName().contentEquals("value")) {
                return name + "(" + annotationValue(entry.getValue()) + ")";
            }
        }
        return values.entrySet().stream()
            .map(e -> e.getKey().getSimpleName() + " = " + annotationValue(e.getValue()))
            .collect(Collectors.joining(", ", name + "(", ")"));
    }

    private static String annotationValue(AnnotationValue value) {
        return value.accept(new SimpleAnnotationValueVisitor14<String, Void>() {
            @Override
            protected String defaultAction(Object o, Void unused) {
                return value.toString();
            }

            @Override
            public String visitType(TypeMirror t, Void unused) {
                return typeName(t, false) + ".class";
            }

            @Override
            public String visitEnumConstant(VariableElement c, Void unused) {
                return className((TypeElement) c.getEnclosingElement(), false) + "." + c.getSimpleName();
            }

            @Override
            public String visitAnnotation(AnnotationMirror a, Void unused) {
                return annotation(a);
            }

            @Override
            public String visitArray(List<? extends AnnotationValue> values, Void unused) {
                if (values.size() == 1) {
                    return annotationValue(values.getFirst());
                }
                return values.stream()
                    .map(DependencySourceFormatter::annotationValue)
                    .collect(Collectors.joining(", ", "{", "}"));
            }
        }, null);
    }
}

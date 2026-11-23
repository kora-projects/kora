package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Arrays;
import java.util.List;


public final class JsonUtils {

    private JsonUtils() {}

    private static final List<String> RESTRICTED_PACKAGES = List.of("java.", "javax.", "sun.", "com.sun.", "jdk.", "kotlin.");

    public static boolean isNativePackage(Elements elements, TypeElement element) {
        PackageElement packageOf = elements.getPackageOf(element);
        return RESTRICTED_PACKAGES.stream().anyMatch(s -> packageOf.getQualifiedName().toString().startsWith(s));
    }

    public static String jsonClassPackage(Elements elements, Element typeElement) {
        return elements.getPackageOf(typeElement).getQualifiedName().toString();
    }

    public static String jsonWriterName(Element typeElement) {
        return NameUtils.generatedType(typeElement, "JsonWriter");
    }

    public static String jsonWriterName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);

        return jsonWriterName(typeElement);
    }

    public static String jsonReaderName(TypeElement typeElement) {
        return NameUtils.generatedType(typeElement, "JsonReader");
    }

    public static String jsonReaderName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);

        return jsonReaderName((TypeElement) typeElement);
    }

    @Nullable
    public static VariableElement fieldByParameter(TypeElement jsonClass, VariableElement param) {
        for (var e : jsonClass.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!e.getSimpleName().toString().equals(param.getSimpleName().toString())) {
                continue;
            }
            var element = (VariableElement) e;
            if (element.asType().toString().equals(param.asType().toString())) {
                return element;
            } else {
                return null;
            }
        }
        return null;
    }


    @Nullable
    public static String discriminatorField(Types types, TypeElement element) {
        var discriminator = discriminator(types, element);
        return discriminator == null
            ? null
            : discriminator.field();
    }

    @Nullable
    public static Discriminator discriminator(Types types, TypeElement element) {
        if (element.getModifiers().contains(Modifier.SEALED)) {
            var annotation = AnnotationUtils.findAnnotation(element, JsonTypes.jsonDiscriminatorField);
            if (annotation != null) {
                return new Discriminator(
                    AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value"),
                    AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "defaultValue")
                );
            }
        }
        var superClass = types.asElement(element.getSuperclass());
        if (superClass instanceof TypeElement && superClass.getModifiers().contains(Modifier.SEALED)) {
            var annotation = AnnotationUtils.findAnnotation(superClass, JsonTypes.jsonDiscriminatorField);
            if (annotation != null) {
                return new Discriminator(
                    AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value"),
                    AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "defaultValue")
                );
            }
        }
        for (var directSupertype : types.directSupertypes(element.asType())) {
            if (directSupertype.toString().equals("java.lang.Object")) {
                continue;
            }
            var superelement = types.asElement(directSupertype);
            var discriminator = discriminator(types, (TypeElement) superelement);
            if (discriminator != null) {
                return discriminator;
            }
        }
        return null;
    }

    public static List<String> discriminatorValue(TypeElement element) {
        var annotation = AnnotationUtils.findAnnotation(element, JsonTypes.jsonDiscriminatorValue);
        if (annotation != null) {
            var value = AnnotationUtils.<List<String>>parseAnnotationValueWithoutDefault(annotation, "value");
            if (value != null) {
                if (value.isEmpty()) {
                    throw new ProcessingErrorException("""
                        Json discriminator value can't be empty:
                          %s

                        Problem:
                          @JsonDiscriminatorValue declares an empty value array.

                        Hint:
                          A sealed JSON subtype must have at least one discriminator value so incoming JSON can be matched to that subtype.

                        Fix:
                          Add at least one discriminator value, or remove @JsonDiscriminatorValue to use the subtype simple name by default.
                        """.formatted(element), element, annotation);
                }
                return value;
            }
        }
        return List.of(element.getSimpleName().toString());
    }

    /**
     * Sealed subtype type expressed in the sealed root type variables: {@code Ok<V> implements Response<V>} becomes {@code Ok<T>} for {@code Response<T>}.
     * Subtype variables that do not map to a root variable become wildcards.
     */
    public static TypeName sealedSubtypeTypeName(Types types, TypeElement sealedElement, TypeElement subtype) {
        var subtypeParameters = subtype.getTypeParameters();
        if (subtypeParameters.isEmpty()) {
            return ClassName.get(subtype);
        }
        var typeArguments = new TypeName[subtypeParameters.size()];
        Arrays.fill(typeArguments, WildcardTypeName.subtypeOf(Object.class));
        var supertype = findSupertype(types, subtype.asType(), types.erasure(sealedElement.asType()));
        if (supertype != null) {
            var supertypeArguments = supertype.getTypeArguments();
            for (int i = 0; i < supertypeArguments.size(); i++) {
                if (supertypeArguments.get(i) instanceof TypeVariable tv) {
                    var index = subtypeParameters.indexOf(tv.asElement());
                    var rootVariable = TypeVariableName.get(sealedElement.getTypeParameters().get(i));
                    if (index >= 0 && typeArguments[index] instanceof WildcardTypeName && TypeVariableName.get(subtypeParameters.get(index)).bounds().equals(rootVariable.bounds())) {
                        typeArguments[index] = rootVariable;
                    }
                }
            }
        }
        return ParameterizedTypeName.get(ClassName.get(subtype), typeArguments);
    }

    @Nullable
    private static DeclaredType findSupertype(Types types, TypeMirror type, TypeMirror sealedErasure) {
        for (var directSupertype : types.directSupertypes(type)) {
            if (types.isSameType(types.erasure(directSupertype), sealedErasure)) {
                return (DeclaredType) directSupertype;
            }
            var supertype = findSupertype(types, directSupertype, sealedErasure);
            if (supertype != null) {
                return supertype;
            }
        }
        return null;
    }

    public record Discriminator(String field, @Nullable String defaultValue) {}
}

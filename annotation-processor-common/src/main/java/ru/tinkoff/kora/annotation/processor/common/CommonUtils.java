package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.NamingStrategy;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.naming.NameConverter;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommonUtils {
    public static String decapitalize(String s) {
        var firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) {
            return s;
        }
        return Character.toLowerCase(firstChar) + s.substring(1);
    }

    public static String capitalize(String s) {
        var firstChar = s.charAt(0);
        if (Character.isUpperCase(firstChar)) {
            return s;
        }
        return Character.toUpperCase(firstChar) + s.substring(1);
    }

    public static boolean isNullable(Element element) {
        var isNullable = element.getAnnotationMirrors()
            .stream()
            .anyMatch(a -> a.getAnnotationType().toString().endsWith(".Nullable"));

        if (isNullable || element.getKind() != ElementKind.RECORD_COMPONENT) {
            return isNullable;
        }

        return element.getEnclosingElement().getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> e.getSimpleName().contentEquals(element.getSimpleName()))
            .anyMatch(CommonUtils::isNullable);
    }

    public static void safeWriteTo(ProcessingEnvironment processingEnv, JavaFile file) {
        try {
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public static AnnotationMirror findAnnotation(Elements elements, Types types, Element element, TypeMirror annotationType) {
        for (var a : elements.getAllAnnotationMirrors(element)) {
            if (types.isSameType(a.getAnnotationType(), annotationType)) {
                return a;
            }
        }
        return null;
    }

    @Nullable
    public static AnnotationMirror findAnnotation(Elements elements, Element element, ClassName annotationType) {
        for (var a : elements.getAllAnnotationMirrors(element)) {
            if (a.getAnnotationType().toString().equals(annotationType.canonicalName())) {
                return a;
            }
        }
        return null;
    }

    @Nullable
    public static AnnotationMirror findDirectAnnotation(Element element, ClassName annotationType) {
        for (var a : element.getAnnotationMirrors()) {
            if (a.getAnnotationType().toString().equals(annotationType.canonicalName())) {
                return a;
            }
        }
        return null;
    }

    public static List<AnnotationMirror> findRepeatableAnnotationsOnElement(Element element, ClassName annotationType, ClassName containerClassName) {
        var result = new ArrayList<AnnotationMirror>(2);
        for (var a : element.getAnnotationMirrors()) {
            var annotationTypeName = (TypeElement) a.getAnnotationType().asElement();
            if (annotationTypeName.getQualifiedName().contentEquals(annotationType.canonicalName())) {
                result.add(a);
            }
        }
        for (var a : element.getAnnotationMirrors()) {
            var containerAnnotationType = (TypeElement) a.getAnnotationType().asElement();
            if (containerAnnotationType.getQualifiedName().contentEquals(containerClassName.canonicalName())) {
                @SuppressWarnings("unchecked")
                var value = (List<AnnotationValue>) a.getElementValues().values().iterator().next().getValue();
                for (var annotationValue : value) {
                    var am = (AnnotationMirror) annotationValue.getValue();
                    result.add(am);
                }
            }
        }
        return result;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T parseAnnotationValue(Elements elements, @Nullable AnnotationMirror annotationMirror, String name) {
        if (annotationMirror == null) {
            return null;
        }
        var annotationValues = elements.getElementValuesWithDefaults(annotationMirror);
        for (var entry : annotationValues.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                var value = entry.getValue();
                if (value == null) {
                    return null;
                }
                return (T) value.getValue();
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T parseAnnotationValueWithoutDefault(@Nullable AnnotationMirror annotationMirror, String name) {
        if (annotationMirror == null) {
            return null;
        }
        var annotationValues = annotationMirror.getElementValues();
        for (var entry : annotationValues.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                var value = entry.getValue();
                if (value == null) {
                    return (T) value;
                }
                if (value.getValue() instanceof List<?> list) {
                    var result = list.stream().map(v -> (AnnotationValue) v).map(AnnotationValue::getValue).toList();
                    return (T) result;
                }
                return (T) value.getValue();
            }
        }
        return null;
    }

    @Deprecated
    public static String getOuterClassesAsPrefix(Element element) {
        return NameUtils.getOuterClassesAsPrefix(element);
    }

    public static String generatedName(Element element, ClassName suffix) {
        return generatedName(element, suffix.simpleName());
    }

    public static String generatedName(Element element, String suffix) {
        var prefix = new StringBuilder("$");
        var parent = element.getEnclosingElement();
        while (parent.getKind() != ElementKind.PACKAGE) {
            prefix.insert(1, parent.getSimpleName().toString() + "_");
            parent = parent.getEnclosingElement();
        }
        return prefix.toString() + element.getSimpleName() + "_" + suffix;
    }

    public static List<ExecutableElement> findConstructors(TypeElement typeElement, Predicate<Set<Modifier>> modifiersFilter) {
        var result = new ArrayList<ExecutableElement>();
        for (var element : typeElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (!modifiersFilter.test(element.getModifiers())) {
                continue;
            }
            result.add((ExecutableElement) element);
        }
        return result;
    }

    public static boolean hasDefaultConstructorAndFinal(Types types, TypeMirror typeMirror) {
        var typeElement = (TypeElement) types.asElement(typeMirror);
        return hasDefaultConstructorAndFinal(typeElement);
    }

    public static boolean hasDefaultConstructorAndFinal(TypeElement typeElement) {
        if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
            return false;
        }
        for (var enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            var constructor = (ExecutableElement) enclosedElement;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (constructor.getParameters().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static List<ExecutableElement> findMethods(TypeElement typeElement, Predicate<Set<Modifier>> modifiersFilter) {
        var result = new ArrayList<ExecutableElement>();
        for (var element : typeElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!modifiersFilter.test(element.getModifiers())) {
                continue;
            }
            result.add((ExecutableElement) element);
        }
        return result;
    }

    public static TypeMirror[] parseTagValue(Element element) {
        return parseAnnotationClassValue(element, Tag.class.getName());
    }

    public static boolean isFinal(Types types, TypeMirror mapperClass) {
        var element = (TypeElement) types.asElement(mapperClass);
        return element.getModifiers().contains(Modifier.FINAL);
    }

    public record MappersData(@Nullable List<TypeMirror> mapperClasses, Set<String> mapperTags) {
        @Nullable
        public MappingData getMapping(Types types, TypeMirror type) {
            if (this.mapperClasses == null && this.mapperTags.isEmpty()) {
                return null;
            }
            for (var mapperClass : Objects.requireNonNullElse(mapperClasses, List.<TypeMirror>of())) {
                if (types.isAssignable(mapperClass, type)) {
                    return new MappingData(mapperClass, this.mapperTags);
                }
            }
            if (this.mapperTags.isEmpty()) {
                return null;
            }
            return new MappingData(null, this.mapperTags);
        }

        @Nullable
        public MappingData getMapping(ClassName type) {
            if (this.mapperClasses == null) {
                return null;
            }
            for (var mapperClass : mapperClasses) {
                if (doesImplement(mapperClass, type)) {
                    return new MappingData(mapperClass, this.mapperTags);
                }
            }
            if (this.mapperTags.isEmpty()) {
                return null;
            }
            return new MappingData(null, this.mapperTags);
        }

        public boolean isEmpty() {
            return this.mapperTags == null && (this.mapperClasses == null || this.mapperClasses.isEmpty());
        }

        @Nullable
        public MappingData first() {
            if (isEmpty()) {
                return null;
            }
            if (this.mapperClasses == null || this.mapperClasses.isEmpty()) {
                return new MappingData(null, this.mapperTags);
            }
            return new MappingData(this.mapperClasses.get(0), this.mapperTags);
        }
    }

    public static boolean doesImplement(TypeMirror type, ClassName i) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        var declaredType = (DeclaredType) type;
        var typeElement = (TypeElement) declaredType.asElement();
        for (var anInterface : typeElement.getInterfaces()) {
            var interfaceType = TypeName.get(anInterface);
            if (interfaceType instanceof ParameterizedTypeName ptn) {
                if (ptn.rawType.equals(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record MappingData(@Nullable TypeMirror mapperClass, Set<String> mapperTags) {
        @Nullable
        public AnnotationSpec toTagAnnotation() {
            return CommonUtils.toTagAnnotation(mapperTags);
        }
    }

    @Nullable
    public static AnnotationSpec toTagAnnotation(Set<String> t) {
        if (t == null || t.isEmpty()) {
            return null;
        }

        var tags = CodeBlock.builder().add("{");
        for (var i = t.iterator(); i.hasNext(); ) {
            var tag = i.next();
            tags.add("$L.class", tag);
            if (i.hasNext()) {
                tags.add(", ");
            }
        }
        tags.add("}");
        return AnnotationSpec.builder(Tag.class).addMember("value", tags.build()).build();
    }

    public static MappersData parseMapping(Element element) {
        var tag = TagUtils.parseTagValue(element);
        if (element.getAnnotationsByType(Mapping.class).length == 0 && tag.isEmpty()) {
            return new MappersData(null, tag);
        }
        var mapping = Stream.of(element.getAnnotationsByType(Mapping.class))
            .map(m -> {
                try {
                    m.value();
                    throw new IllegalStateException();
                } catch (MirroredTypeException e) {
                    return e.getTypeMirror();
                }
            })
            .collect(Collectors.toList());

        return new MappersData(mapping, tag);
    }

    public static TypeMirror[] parseAnnotationClassValue(Element element, String annotationName) {
        return parseAnnotationClassValue(element, annotationName, "value");
    }

    @SuppressWarnings("unchecked")
    public static TypeMirror[] parseAnnotationClassValue(Element element, String annotationName, String fieldName) {
        return element.getAnnotationMirrors()
            .stream()
            .filter(m -> ((TypeElement) (m.getAnnotationType().asElement())).getQualifiedName().toString().equals(annotationName))
            .findFirst()
            .map(m -> m.getElementValues().entrySet()
                .stream()
                .filter(e -> e.getKey().getSimpleName().toString().equals(fieldName))
                .map(Map.Entry::getValue)
                .map(AnnotationValue::getValue)
                .flatMap(l -> ((List<AnnotationValue>) l).stream())
                .map(AnnotationValue::getValue)
                .map(TypeMirror.class::cast)
                .toArray(TypeMirror[]::new)
            )
            .orElseGet(() -> new TypeMirror[0]);
    }

    public static CodeBlock writeTagAnnotationValue(TypeMirror[] tag) {
        var b = CodeBlock.builder()
            .add("{");
        for (var typeMirrorValue : tag) {
            b.add("$T.class, ", typeMirrorValue);
        }
        return b.add("}").build();
    }


    public static Class<?> getNamingStrategyConverterClass(Element element) {
        var typeMirrors = parseAnnotationClassValue(element, NamingStrategy.class.getName());
        if (typeMirrors.length == 0) return null;
        var mirror = typeMirrors[0];
        if (mirror instanceof DeclaredType) {
            if (((DeclaredType) mirror).asElement() instanceof TypeElement) {
                var className = ((TypeElement) ((DeclaredType) mirror).asElement()).getQualifiedName().toString();
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new ProcessingErrorException("Class " + className + " not found in classpath", element);
                }
            }
        }
        return null;
    }

    @Nullable
    public static NameConverter getNameConverter(TypeElement typeElement) {
        var namingStrategy = typeElement.getAnnotation(NamingStrategy.class);
        NameConverter nameConverter = null;
        if (namingStrategy != null) {
            var namingStrategyClass = getNamingStrategyConverterClass(typeElement);
            if (namingStrategyClass != null) {
                try {
                    nameConverter = (NameConverter) namingStrategyClass.getConstructor().newInstance();
                } catch (Exception e) {
                    throw new ProcessingErrorException("Error on calling name converter constructor " + typeElement, typeElement);
                }
            }
        }
        return nameConverter;
    }

    public static TypeSpec.Builder extendsKeepAop(TypeElement type, String newName) {
        var b = TypeSpec.classBuilder(newName)
            .addModifiers(Modifier.PUBLIC)
            .addOriginatingElement(type);
        if (type.getKind() == ElementKind.INTERFACE) {
            b.addSuperinterface(type.asType());
        } else {
            b.superclass(type.asType());
        }

        var hasAop = false;
        for (var annotationMirror : type.getAnnotationMirrors()) {
            if (CommonUtils.isAopAnnotation(annotationMirror)) {
                b.addAnnotation(AnnotationSpec.get(annotationMirror));
                hasAop = true;
            }
        }

        if (!hasAop && !hasAopAnnotations(type)) {
            b.addModifiers(Modifier.FINAL);
        }

        return b;
    }

    public static MethodSpec.Builder overridingKeepAop(ExecutableElement method) {
        var type = (ExecutableType) method.asType();
        return overridingKeepAop(method, type);
    }

    public static MethodSpec.Builder overridingKeepAop(ExecutableElement method, ExecutableType methodType) {
        var methodBuilder = MethodSpec.methodBuilder(method.getSimpleName().toString());
        if (method.getModifiers().contains(Modifier.PUBLIC)) {
            methodBuilder.addModifiers(Modifier.PUBLIC);
        }
        if (method.getModifiers().contains(Modifier.PROTECTED)) {
            methodBuilder.addModifiers(Modifier.PROTECTED);
        }
        for (var typeParameterElement : method.getTypeParameters()) {
            var var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }
        methodBuilder.addAnnotation(Override.class);
        for (var annotationMirror : method.getAnnotationMirrors()) {
            if (CommonUtils.isAopAnnotation(annotationMirror) || annotationMirror.getAnnotationType().toString().endsWith(".Nullable")) {
                methodBuilder.addAnnotation(AnnotationSpec.get(annotationMirror));
            }
        }


        methodBuilder.returns(TypeName.get(methodType.getReturnType()));
        for (int i = 0; i < method.getParameters().size(); i++) {
            var parameter = method.getParameters().get(i);
            var parameterType = methodType.getParameterTypes().get(i);
            var name = parameterType.toString().startsWith("kotlin.coroutines.Continuation")
                ? "_continuation"
                : parameter.getSimpleName().toString();
            var pb = ParameterSpec.builder(TypeName.get(parameterType), name);
            for (var annotationMirror : parameter.getAnnotationMirrors()) {
                if (CommonUtils.isAopAnnotation(annotationMirror) || annotationMirror.getAnnotationType().toString().endsWith(".Nullable")) {
                    pb.addAnnotation(AnnotationSpec.get(annotationMirror));
                }
            }
            methodBuilder.addParameter(pb.build());
        }
        methodBuilder.varargs(method.isVarArgs());
        for (var thrownType : methodType.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
        }

        return methodBuilder;
    }


    public static boolean hasAopAnnotations(TypeElement typeElement) {
        if (CommonUtils.hasAopAnnotation(typeElement)) {
            return true;
        }
        var methods = CommonUtils.findMethods(typeElement, m -> m.contains(Modifier.PUBLIC) || m.contains(Modifier.PROTECTED));
        for (var method : methods) {
            if (hasAopAnnotation(method)) {
                return true;
            }
            for (var parameter : method.getParameters()) {
                if (hasAopAnnotation(parameter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAopAnnotation(Element e) {
        return e.getAnnotationMirrors().stream().anyMatch(CommonUtils::isAopAnnotation);
    }

    private static boolean isAopAnnotation(AnnotationMirror am) {
        return am.getAnnotationType().asElement().getAnnotation(AopAnnotation.class) != null;
    }

    public static boolean isVoid(TypeMirror returnType) {
        final String typeAsStr = returnType.toString();
        return returnType.getKind().equals(TypeKind.VOID)
               || Void.class.getCanonicalName().equals(typeAsStr)
               || "void".equals(typeAsStr);
    }

    public static boolean isList(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();
        return name.equals(List.class.getCanonicalName())
               || name.equals(ArrayList.class.getCanonicalName())
               || name.equals(LinkedList.class.getCanonicalName());
    }

    public static boolean isSet(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();
        return name.equals(Set.class.getCanonicalName())
               || name.equals(HashSet.class.getCanonicalName())
               || name.equals(TreeSet.class.getCanonicalName())
               || name.equals(SortedSet.class.getCanonicalName())
               || name.equals(LinkedHashSet.class.getCanonicalName())
               || name.equals(CopyOnWriteArraySet.class.getCanonicalName())
               || name.equals(ConcurrentSkipListSet.class.getCanonicalName());
    }

    public static boolean isQueue(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
               && type instanceof DeclaredType dt
               && (dt.asElement().toString().equals(Queue.class.getCanonicalName())
                   || dt.asElement().toString().equals(Deque.class.getCanonicalName()));
    }

    public static boolean isCollection(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
               && type instanceof DeclaredType dt
               && (dt.asElement().toString().equals(Collection.class.getCanonicalName())
                   || isList(type)
                   || isSet(type)
                   || isQueue(type));
    }

    public static boolean isMap(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();

        return name.equals(Map.class.getCanonicalName())
               || name.equals(HashMap.class.getCanonicalName())
               || name.equals(TreeMap.class.getCanonicalName())
               || name.equals(LinkedHashMap.class.getCanonicalName())
               || name.equals(ConcurrentMap.class.getCanonicalName())
               || name.equals(ConcurrentHashMap.class.getCanonicalName())
               || name.equals(SortedMap.class.getCanonicalName())
               || name.equals(NavigableMap.class.getCanonicalName())
               || name.equals(ConcurrentSkipListMap.class.getCanonicalName())
               || name.equals(IdentityHashMap.class.getCanonicalName())
               || name.equals(WeakHashMap.class.getCanonicalName())
               || name.equals(EnumMap.class.getCanonicalName());
    }

    public static boolean isOptional(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
               && type instanceof DeclaredType dt
               && dt.asElement().toString().equals(Optional.class.getCanonicalName());
    }

    public static boolean isMono(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
               && type instanceof DeclaredType dt
               && dt.asElement().toString().equals(CommonClassNames.mono.canonicalName());
    }

    public static boolean isFlux(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
               && type instanceof DeclaredType dt
               && dt.asElement().toString().equals(CommonClassNames.flux.canonicalName());
    }

    public static boolean isFuture(TypeMirror type) {
        if(type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        if(!(type instanceof DeclaredType dt)) {
            return false;
        }

        final String name = dt.asElement().toString();
        return name.equals(CompletableFuture.class.getCanonicalName())
                   || name.equals(Future.class.getCanonicalName())
                   || name.equals(RunnableFuture.class.getCanonicalName())
                   || name.equals(FutureTask.class.getCanonicalName());
    }
}

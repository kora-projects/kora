package ru.tinkoff.kora.aop.annotation.processor;

import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Generated;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

public class AopProcessor {
    private static final Logger log = LoggerFactory.getLogger(AopProcessor.class);

    private final List<KoraAspect> aspects;
    private final Types types;
    private final Elements elements;
    private final TypeMirror tagAnnotationTypeMirror;

    public AopProcessor(Types types, Elements elements, List<KoraAspect> aspects) {
        this.aspects = aspects;
        this.types = types;
        this.elements = elements;
        this.tagAnnotationTypeMirror = this.elements.getTypeElement(Tag.class.getCanonicalName())
            .asType();
    }

    private static class TypeFieldFactory implements KoraAspect.FieldFactory {
        private final Types types;
        private final Set<String> fieldNames = new HashSet<>();
        private final Map<ConstructorParamKey, String> constructorParams = new LinkedHashMap<>();
        private final Map<ConstructorInitializedParamKey, String> constructorInitializedParams = new LinkedHashMap<>();

        private record ConstructorParamKey(TypeMirror type, List<AnnotationSpec> annotations, Types types) {
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof ConstructorParamKey that) {
                    if (!that.annotations().equals(this.annotations())) {
                        return false;
                    }
                    return this.types.isSameType(this.type(), that.type());
                }
                return false;
            }

            @Override
            public int hashCode() {
                return 31 * annotations().hashCode() + type().toString().hashCode();
            }
        }

        private record ConstructorInitializedParamKey(TypeMirror type, CodeBlock initializer, Types types) {
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof ConstructorInitializedParamKey that) {
                    if (!that.initializer.equals(this.initializer)) {
                        return false;
                    }
                    return this.types.isSameType(this.type, that.type);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return 31 * this.initializer.hashCode() + this.type.toString().hashCode();
            }
        }

        private TypeFieldFactory(Types types) {
            this.types = types;
        }

        public void addFields(TypeSpec.Builder typeBuilder) {
            for (var entry : this.constructorParams.entrySet()) {
                var name = entry.getValue();
                var fd = entry.getKey();
                typeBuilder.addField(FieldSpec.builder(TypeName.get(fd.type()), name, Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
            for (var entry : this.constructorInitializedParams.entrySet()) {
                var name = entry.getValue();
                var fd = entry.getKey();
                typeBuilder.addField(FieldSpec.builder(TypeName.get(fd.type()), name, Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
        }

        @Override
        public String constructorParam(TypeMirror type, List<AnnotationSpec> annotations) {
            return this.constructorParams.computeIfAbsent(new ConstructorParamKey(type, annotations, this.types), this::computeFieldName);
        }

        @Override
        public String constructorInitialized(TypeMirror type, CodeBlock initializer) {
            return this.constructorInitializedParams.computeIfAbsent(new ConstructorInitializedParamKey(type, initializer, this.types), this::computeFieldName);
        }

        public void enrichConstructor(MethodSpec.Builder constructorBuilder) {
            for (var entry : this.constructorParams.entrySet()) {
                var name = entry.getValue();
                var fd = entry.getKey();
                constructorBuilder.addParameter(ParameterSpec.builder(TypeName.get(fd.type()), name)
                    .addAnnotations(fd.annotations())
                    .build());
                constructorBuilder.addCode("this.$L = $L;\n", name, name);
            }
            for (var entry : this.constructorInitializedParams.entrySet()) {
                var name = entry.getValue();
                var fd = entry.getKey();
                constructorBuilder.addCode("this.$L = $L;\n", name, fd.initializer);
            }
        }

        private String computeFieldName(ConstructorParamKey key) {
            var qualifiedType = key.type().toString();
            if (qualifiedType.indexOf('<') > 0) {
                qualifiedType = qualifiedType.substring(0, qualifiedType.indexOf('<'));
            }

            var dotIndex = qualifiedType.lastIndexOf('.');
            var shortName = dotIndex < 0
                ? CommonUtils.decapitalize(qualifiedType)
                : CommonUtils.decapitalize(qualifiedType.substring(dotIndex + 1));
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                var name = shortName + i;
                if (this.fieldNames.add(name)) {
                    return name;
                }
            }
            // never gonna happen

            throw new IllegalStateException("Can't compute name for " + key);
        }

        private String computeFieldName(ConstructorInitializedParamKey key) {
            var qualifiedType = key.type().toString();
            if (qualifiedType.indexOf('<') > 0) {
                qualifiedType = qualifiedType.substring(0, qualifiedType.indexOf('<'));
            }

            var dotIndex = qualifiedType.lastIndexOf('.');
            var shortName = dotIndex < 0
                ? CommonUtils.decapitalize(qualifiedType)
                : CommonUtils.decapitalize(qualifiedType.substring(dotIndex + 1));
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                var name = shortName + i;
                if (this.fieldNames.add(name)) {
                    return name;
                }
            }
            // never gonna happen
            return null;
        }
    }


    public TypeSpec applyAspects(TypeElement typeElement) {
        var constructor = AopUtils.findAopConstructor(typeElement);
        if (constructor == null) {
            throw new ProcessingErrorException("Class has no aop suitable constructor", typeElement);
        }
        var typeLevelAspects = new ArrayList<KoraAspect>();
        for (var am : typeElement.getAnnotationMirrors()) {
            for (var aspect : this.aspects) {
                if (aspect.getSupportedAnnotationTypes().contains(am.getAnnotationType().toString())) {
                    if (!typeLevelAspects.contains(aspect)) {
                        typeLevelAspects.add(aspect);
                    }
                }
            }
        }
        log.debug("Type level aspects for {}: {}", typeElement, typeLevelAspects);

        var typeFieldFactory = new TypeFieldFactory(this.types);
        var aopContext = new KoraAspect.AspectContext(typeFieldFactory);

        var typeBuilder = TypeSpec.classBuilder(AopUtils.aopProxyName(typeElement))
            .superclass(typeElement.asType())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        var tag = CommonUtils.findAnnotation(this.elements, this.types, typeElement, this.tagAnnotationTypeMirror);
        if (tag != null) {
            typeBuilder.addAnnotation(AnnotationSpec.get(tag));
        }

        var appliedProcessors = new LinkedHashSet<String>();
        appliedProcessors.add(AopAnnotationProcessor.class.getCanonicalName());

        var typeMethods = CommonUtils.findMethods(typeElement, m -> !m.contains(Modifier.STATIC) && (m.contains(Modifier.PROTECTED) || m.contains(Modifier.PUBLIC)));
        for (var typeMethod : typeMethods) {
            var methodLevelTypeAspects = new ArrayList<>(typeLevelAspects);
            var methodLevelAspects = new ArrayList<KoraAspect>();
            var methodParameterLevelAspects = new ArrayList<KoraAspect>();
            for (var am : typeMethod.getAnnotationMirrors()) {
                for (var aspect : this.aspects) {
                    if (aspect.getSupportedAnnotationTypes().contains(am.getAnnotationType().toString())) {
                        if (!methodLevelAspects.contains(aspect)) {
                            methodLevelAspects.add(aspect);
                        }
                        methodLevelTypeAspects.remove(aspect);
                    }
                }
            }
            for (var parameter : typeMethod.getParameters()) {
                for (var am : parameter.getAnnotationMirrors()) {
                    for (var aspect : this.aspects) {
                        if (aspect.getSupportedAnnotationTypes().contains(am.getAnnotationType().toString())) {
                            if (!methodParameterLevelAspects.contains(aspect) && !methodLevelAspects.contains(aspect)) {
                                methodParameterLevelAspects.add(aspect);
                            }
                            methodLevelTypeAspects.remove(aspect);
                        }
                    }
                }
            }

            if (methodLevelTypeAspects.isEmpty() && methodLevelAspects.isEmpty() && methodParameterLevelAspects.isEmpty()) {
                continue;
            }
            log.debug("Method level aspects for {}#{}: {}", typeElement, typeMethod.getSimpleName(), methodLevelAspects);
            var aspectsToApply = new ArrayList<>(methodLevelTypeAspects);
            aspectsToApply.addAll(methodLevelAspects);
            aspectsToApply.addAll(methodParameterLevelAspects);
            var superCall = "super." + typeMethod.getSimpleName();
            var overridenMethod = MethodSpec.overriding(typeMethod);
            Collections.reverse(aspectsToApply);

            for (var aspect : aspectsToApply) {
                var result = aspect.apply(typeMethod, superCall, aopContext);
                if (result instanceof KoraAspect.ApplyResult.Noop) {
                    continue;
                }

                var methodBody = (KoraAspect.ApplyResult.MethodBody) result;
                var methodName = "_" + typeMethod.getSimpleName() + "_AopProxy_" + aspect.getClass().getSimpleName();
                superCall = methodName;
                var m = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PRIVATE)
                    .addCode(methodBody.codeBlock());
                for (var parameter : typeMethod.getParameters()) {
                    m.addParameter(ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.getSimpleName().toString()).build());
                }
                for (var typeParameter : typeMethod.getTypeParameters()) {
                    m.addTypeVariable(TypeVariableName.get(typeParameter));
                }
                for (var exception : typeMethod.getThrownTypes()) {
                    m.addException(TypeName.get(exception));
                }
                m.returns(TypeName.get(typeMethod.getReturnType()));
                typeBuilder.addMethod(m.build());

                appliedProcessors.add(aspect.getClass().getCanonicalName());
            }
            var b = CodeBlock.builder();
            if (typeMethod.getReturnType().getKind() != TypeKind.VOID) {
                b.add("return ");
            }
            b.add("this.$L(", superCall);
            for (int i = 0; i < typeMethod.getParameters().size(); i++) {
                if (i > 0) {
                    b.add(", ");
                }
                var parameter = typeMethod.getParameters().get(i);
                b.add("$L", parameter);
            }
            b.add(");\n");
            overridenMethod.addCode(b.build());
            typeBuilder.addMethod(overridenMethod.build());
        }

        var generated = appliedProcessors.stream()
            .map(a -> CodeBlock.of("$S", a))
            .collect(CodeBlock.joining(", ", "{", "}"));

        typeBuilder
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", generated)
                .build());

        if (typeElement.getAnnotation(Component.class) != null) {
            typeBuilder.addAnnotation(Component.class);
        }

        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        constructorBuilder.addCode("super(");
        for (int i = 0; i < constructor.getParameters().size(); i++) {
            if (i > 0) {
                constructorBuilder.addCode(", ");
            }
            var parameter = constructor.getParameters().get(i);
            constructorBuilder.addCode("$L", parameter);
            var parameterSpec = ParameterSpec.get(parameter);
            var tags = TagUtils.parseTagValue(parameter);

            if (!tags.isEmpty()) {
                parameterSpec = parameterSpec.toBuilder().addAnnotation(TagUtils.makeAnnotationSpec(tags)).build();
            }

            constructorBuilder.addParameter(parameterSpec);
        }
        constructorBuilder.addCode(");\n");
        typeFieldFactory.addFields(typeBuilder);
        typeFieldFactory.enrichConstructor(constructorBuilder);
        typeBuilder.addMethod(constructorBuilder.build());

        return typeBuilder.build();
    }
}

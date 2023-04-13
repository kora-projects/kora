package ru.tinkoff.kora.validation.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;
import ru.tinkoff.kora.validation.annotation.processor.ValidMeta;
import ru.tinkoff.kora.validation.annotation.processor.ValidatorGenerator;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public final class ValidKoraExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final TypeMirror validatorType;
    private final ValidatorGenerator generator;
    private final ProcessingEnvironment processingEnv;

    public ValidKoraExtension(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.validatorType = types.erasure(elements.getTypeElement("ru.tinkoff.kora.validation.common.Validator").asType());
        this.generator = new ValidatorGenerator(processingEnv);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        var erasure = types.erasure(typeMirror);
        if (!types.isSameType(erasure, validatorType)) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType dt)) {
            return null;
        }
        var validatorArgumentType = dt.getTypeArguments().get(0);
        if (validatorArgumentType.getKind() != TypeKind.DECLARED) {
            return null;
        }

        var validatedTypeElement = types.asElement(validatorArgumentType);
        var packageElement = elements.getPackageOf(validatedTypeElement).getQualifiedName().toString();
        var validatorName = CommonUtils.getOuterClassesAsPrefix(validatedTypeElement) + validatedTypeElement.getSimpleName() + "_Validator";
        var componentElement = elements.getTypeElement(packageElement + "." + validatorName);

        if (componentElement != null) {
            return () -> buildExtensionResult((DeclaredType) validatorArgumentType, componentElement);
        } else if (AnnotationUtils.findAnnotation(validatedTypeElement, ValidMeta.VALID_TYPE) != null) {
            return ExtensionResult::nextRound;
        } else {
            try {
                this.generator.generateFor((TypeElement) validatedTypeElement);
            } catch (ProcessingErrorException e) {
                for (var error : e.getErrors()) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, error.message(), error.element(), error.a(), error.v());
                }
                return null;
            }
        }

        return null;
    }

    private ExtensionResult buildExtensionResult(DeclaredType componentArgumentType, TypeElement componentElement) {
        var constructor = findDefaultConstructor(componentElement);
        if (componentElement.getTypeParameters().isEmpty()) {
            return ExtensionResult.fromExecutable(constructor);
        }

        var typeTypeParameters = componentArgumentType.getTypeArguments();
        var declaredType = types.getDeclaredType(componentElement, typeTypeParameters.toArray(new TypeMirror[0]));
        var constructorType = (ExecutableType) types.asMemberOf(declaredType, constructor);
        return ExtensionResult.fromExecutable(constructor, constructorType);
    }

    private ExecutableElement findDefaultConstructor(TypeElement componentElement) {
        return componentElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .findFirst()
            .orElseThrow();
    }
}

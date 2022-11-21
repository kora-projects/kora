package ru.tinkoff.kora.database.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class RepositoryKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;

    public RepositoryKoraExtension(ProcessingEnvironment processingEnvironment) {
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
    }

    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = this.types.asElement(typeMirror);
        if (element.getKind() != ElementKind.INTERFACE) {
            return null;
        }
        if (CommonUtils.findDirectAnnotation(element, DbUtils.REPOSITORY_ANNOTATION) == null) {
            return null;
        }
        var typeElement = (TypeElement) this.types.asElement(typeMirror);
        var packageElement = this.elements.getPackageOf(typeElement);
        var repositoryName = CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName().toString() + "_Impl";
        var packageName = packageElement.getQualifiedName();
        return () -> {
            var repositoryElement = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + repositoryName);
            if (repositoryElement == null) {
                // annotation processor will handle it
                return ExtensionResult.nextRound();
            }
            if (!CommonUtils.hasAopAnnotations(repositoryElement)) {
                return CommonUtils.findConstructors(repositoryElement, m -> m.contains(Modifier.PUBLIC)).stream().map(ExtensionResult::fromExecutable).findFirst().orElseThrow();
            }
            var aopProxy = CommonUtils.getOuterClassesAsPrefix(repositoryElement) + repositoryElement.getSimpleName() + "__AopProxy";
            var aopProxyElement = this.elements.getTypeElement(packageName + "." + aopProxy);
            if (aopProxyElement == null) {
                // aop annotation processor will handle it
                return ExtensionResult.nextRound();
            }
            return CommonUtils.findConstructors(aopProxyElement, m -> m.contains(Modifier.PUBLIC)).stream().map(ExtensionResult::fromExecutable).findFirst().orElseThrow();
        };
    }
}

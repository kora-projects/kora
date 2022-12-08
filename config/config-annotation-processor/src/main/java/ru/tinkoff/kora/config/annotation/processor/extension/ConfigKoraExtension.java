package ru.tinkoff.kora.config.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.config.annotation.processor.ConfigParserGenerator;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;

public final class ConfigKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;
    private final TypeMirror configValueExtractorTypeErasure;
    private final ConfigParserGenerator configParserGenerator;
    private final ProcessingEnvironment processingEnv;

    public ConfigKoraExtension(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.configParserGenerator = new ConfigParserGenerator(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.configValueExtractorTypeErasure = types.erasure(elements.getTypeElement(ConfigValueExtractor.class.getCanonicalName()).asType());
    }

    @Override
    @Nullable
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        if (!types.isSameType(types.erasure(typeMirror), configValueExtractorTypeErasure)) {
            return null;
        }

        var paramType = ((DeclaredType) typeMirror).getTypeArguments().get(0);
        if (paramType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = ((TypeElement) types.asElement(paramType));

        if (element.getKind() == ElementKind.RECORD) {
            return () -> this.generateDependency(roundEnvironment, typeMirror);
        }

        if (element.getKind() == ElementKind.CLASS) {
            var constructors = CommonUtils.findConstructors(element, m -> !m.contains(Modifier.PRIVATE));

            return () -> this.generateDependency(roundEnvironment, typeMirror);
        }

        return null;
    }

    public ExtensionResult generateDependency(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
        var targetType = ((DeclaredType) ((DeclaredType) typeMirror).getTypeArguments().get(0));
        var element = ((TypeElement) types.asElement(targetType));
        var packageName = elements.getPackageOf(element).getQualifiedName().toString();
        var typeName = CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_" + ConfigValueExtractor.class.getSimpleName();

        var maybeGenerated = elements.getTypeElement(packageName + "." + typeName);
        if (maybeGenerated != null) {
            var constructor = this.getConstructor(maybeGenerated);

            return ExtensionResult.fromExecutable(constructor);
        }

        var javaFile = this.configParserGenerator.generate(roundEnvironment, targetType);

        try {
            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return ExtensionResult.RequiresCompilingResult.INSTANCE;
    }

    private ExecutableElement getConstructor(Element element) {
        return element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .findFirst()
            .get();
    }

}

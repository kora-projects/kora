package ru.tinkoff.kora.config.annotation.processor.extension;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.config.annotation.processor.ConfigClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class ConfigKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;
    private final TypeMirror configValueExtractorTypeErasure;

    public ConfigKoraExtension(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.configValueExtractorTypeErasure = types.erasure(elements.getTypeElement(ConfigClassNames.configValueExtractor.canonicalName()).asType());
    }

    @Override
    @Nullable
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, String tag) {
        if (tag != null) return null;
        if (!types.isSameType(types.erasure(typeMirror), configValueExtractorTypeErasure)) {
            return null;
        }

        var paramType = ((DeclaredType) typeMirror).getTypeArguments().get(0);
        if (paramType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = ((TypeElement) types.asElement(paramType));
        var mapperName = NameUtils.generatedType(element, ConfigClassNames.configValueExtractor);
        if (AnnotationUtils.isAnnotationPresent(element, ConfigClassNames.configValueExtractorAnnotation) || AnnotationUtils.isAnnotationPresent(element, ConfigClassNames.configSourceAnnotation)) {
            return KoraExtensionDependencyGenerator.generatedFromWithName(this.elements, element, mapperName);
        }
        return null;
    }
}

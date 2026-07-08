package io.koraframework.config.annotation.processor.extension;

import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.config.annotation.processor.ConfigClassNames;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

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
    private final TypeMirror configValueMapperTypeErasure;

    public ConfigKoraExtension(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.configValueMapperTypeErasure = types.erasure(elements.getTypeElement(ConfigClassNames.configValueMapper.canonicalName()).asType());
    }

    @Override
    @Nullable
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, String tag) {
        if (tag != null) return null;
        if (!types.isSameType(types.erasure(typeMirror), configValueMapperTypeErasure)) {
            return null;
        }

        var paramType = ((DeclaredType) typeMirror).getTypeArguments().get(0);
        if (paramType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = ((TypeElement) types.asElement(paramType));
        var mapperName = NameUtils.generatedType(element, ConfigClassNames.configValueMapper);
        if (AnnotationUtils.isAnnotationPresent(element, ConfigClassNames.configValueMapperAnnotation) || AnnotationUtils.isAnnotationPresent(element, ConfigClassNames.configSourceAnnotation)) {
            return KoraExtensionDependencyGenerator.generatedFromWithName(this.elements, element, mapperName);
        }
        return null;
    }
}

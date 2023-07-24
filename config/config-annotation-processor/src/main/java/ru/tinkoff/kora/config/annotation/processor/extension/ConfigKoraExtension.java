package ru.tinkoff.kora.config.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.config.annotation.processor.ConfigClassNames;
import ru.tinkoff.kora.config.annotation.processor.ConfigParserGenerator;
import ru.tinkoff.kora.config.annotation.processor.ConfigUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

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
        this.configValueExtractorTypeErasure = types.erasure(elements.getTypeElement(ConfigClassNames.configValueExtractor.canonicalName()).asType());
    }

    @Override
    @Nullable
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) return null;
        if (!types.isSameType(types.erasure(typeMirror), configValueExtractorTypeErasure)) {
            return null;
        }

        var paramType = ((DeclaredType) typeMirror).getTypeArguments().get(0);
        if (paramType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = ((TypeElement) types.asElement(paramType));
        var packageElement = this.elements.getPackageOf(element);
        var mapperName = CommonUtils.generatedName(element, ConfigClassNames.configValueExtractor);
        if (AnnotationUtils.isAnnotationPresent(element, ConfigClassNames.configValueExtractorAnnotation)) {
            return () -> {
                var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
                if (maybeGenerated == null) {
                    return ExtensionResult.nextRound();
                }
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            };
        }
        var generator = (KoraExtensionDependencyGenerator) () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var result = switch (element.getKind()) {
                case CLASS -> this.configParserGenerator.generateForPojo(roundEnvironment, (DeclaredType) paramType);
                case INTERFACE -> this.configParserGenerator.generateForInterface(roundEnvironment, (DeclaredType) paramType);
                case RECORD -> this.configParserGenerator.generateForRecord(roundEnvironment, (DeclaredType) paramType);
                default -> throw new IllegalStateException();
            };
            assert result.isLeft(); // should be checked
            return ExtensionResult.nextRound();
        };
        if (element.getKind() == ElementKind.INTERFACE || element.getKind() == ElementKind.RECORD || element.getKind() == ElementKind.CLASS) {
            var fields = ConfigUtils.parseFields(this.types, element);
            if (fields.isLeft()) {
                return generator;
            } else {
                var firstError = fields.right().get(0);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Extension tried to generate dependency but failed: " + firstError.message(), firstError.element(), firstError.a(), firstError.v());
                return null;
            }
        }

        return null;
    }
}

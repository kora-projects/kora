package ru.tinkoff.kora.validation.annotation.processor.extension;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Set;

import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.VALIDATOR_TYPE;
import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.VALID_TYPE;

public final class ValidKoraExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;

    public ValidKoraExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) return null;
        if (!(TypeName.get(typeMirror) instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (!ptn.rawType().equals(VALIDATOR_TYPE)) {
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
        if (AnnotationUtils.findAnnotation(validatedTypeElement, VALID_TYPE) == null) {
            return null;
        }
        return KoraExtensionDependencyGenerator.generatedFrom(elements, validatedTypeElement, VALIDATOR_TYPE);
    }
}

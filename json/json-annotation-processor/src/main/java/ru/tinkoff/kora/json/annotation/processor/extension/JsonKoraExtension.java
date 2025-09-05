package ru.tinkoff.kora.json.annotation.processor.extension;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Set;

public class JsonKoraExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;

    public JsonKoraExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
    }

    @Override
    @Nullable
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) return null;
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (ptn.rawType().equals(JsonTypes.jsonWriter)) {
            var writerType = (DeclaredType) typeMirror;
            var possibleJsonClass = writerType.getTypeArguments().get(0);
            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }
            var jsonElement = (TypeElement) this.types.asElement(possibleJsonClass);
            if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonWriterAnnotation) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, jsonElement, JsonTypes.jsonWriter);
            }
            return null;
        }

        if (ptn.rawType().equals(JsonTypes.jsonReader)) {
            var readerType = (DeclaredType) typeMirror;
            var possibleJsonClass = readerType.getTypeArguments().get(0);
            if (possibleJsonClass.getKind() != TypeKind.DECLARED) {
                return null;
            }
            var jsonElement = (TypeElement) types.asElement(possibleJsonClass);
            if (AnnotationUtils.findAnnotation(jsonElement, JsonTypes.json) != null
                || AnnotationUtils.findAnnotation(jsonElement, JsonTypes.jsonReaderAnnotation) != null
                || CommonUtils.findConstructors(jsonElement, s -> s.contains(Modifier.PUBLIC))
                .stream()
                .anyMatch(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null)) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, jsonElement, JsonTypes.jsonReader);
            }
            return null;
        }
        return null;
    }
}

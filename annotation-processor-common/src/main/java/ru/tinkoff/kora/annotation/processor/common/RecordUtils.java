package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.TypeName;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class RecordUtils {
    public static ExecutableElement findCanonicalConstructor(TypeElement record) {
        assert record.getKind() == ElementKind.RECORD;
        var recordComponents = record.getRecordComponents();
        for (var enclosedElement : record.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR || !(enclosedElement instanceof ExecutableElement constructor)) {
                continue;
            }
            if (recordComponents.size() != constructor.getParameters().size()) {
                continue;
            }
            for (int i = 0; i < recordComponents.size(); i++) {
                var recordComponent = recordComponents.get(i);
                var constructorParam = constructor.getParameters().get(i);
                if (!recordComponent.getSimpleName().contentEquals(constructorParam.getSimpleName())) {
                    break;
                }
                var recordComponentType = TypeName.get(recordComponent.asType()).withoutAnnotations();
                var constructorParamType = TypeName.get(constructorParam.asType()).withoutAnnotations();
                if (recordComponentType.equals(constructorParamType)) {
                    return constructor;
                }
            }
        }
        throw new IllegalStateException("Canonical record constructor not found!");
    }
}

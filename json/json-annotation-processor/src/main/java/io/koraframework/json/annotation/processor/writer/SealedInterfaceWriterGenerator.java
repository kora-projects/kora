package io.koraframework.json.annotation.processor.writer;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.json.annotation.processor.JsonUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

import static io.koraframework.annotation.processor.common.CommonUtils.decapitalize;

public class SealedInterfaceWriterGenerator {
    private final Types types;
    private final Elements elements;

    public SealedInterfaceWriterGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    public TypeSpec generateSealedWriter(TypeElement jsonElement, List<? extends Element> jsonElements) {
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonWriterName(jsonElement))
            .addAnnotation(AnnotationUtils.generated(SealedInterfaceWriterGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(jsonElement.asType())))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(jsonElement);
        this.addWriters(typeBuilder, jsonElements);


        for (var typeParameter : jsonElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(jsonElement.asType()).annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class);
        method.beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()");
        var hasUncheckedCast = false;
        for (var elem : jsonElements) {
            var writerName = getWriterFieldName(elem);
            var elemType = subtypeTypeName(elem);
            var typeParameters = ((TypeElement) elem).getTypeParameters();
            if (typeParameters.isEmpty() || JsonUtils.isSealedSubtypeOfParentType(this.types, elem, jsonElement)) {
                method.nextControlFlow("else if (_object instanceof $T _o)", elemType)
                    .addStatement("$L.write(_gen, _o)", writerName);
            } else {
                // a subtype that narrows the parent's type arguments (Left<A> implements Pair<A, Object>) can only be matched by a wildcard
                var wildcards = typeParameters.stream().map(t -> WildcardTypeName.subtypeOf(Object.class)).toArray(TypeName[]::new);
                method.nextControlFlow("else if (_object instanceof $T _o)", ParameterizedTypeName.get(ClassName.get((TypeElement) elem), wildcards))
                    .addStatement("$L.write(_gen, ($T) _o)", writerName, elemType);
                hasUncheckedCast = true;
            }
        }
        if (hasUncheckedCast) {
            method.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build());
        }
        method.nextControlFlow("else")
            .addStatement("throw new $T($S)", IllegalStateException.class, "Unsupported class")
            .endControlFlow();
        typeBuilder.addMethod(method.build());
        return typeBuilder.build();
    }

    private void addWriters(TypeSpec.Builder typeBuilder, List<? extends Element> jsonElements) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        jsonElements.forEach(elem -> {
            var fieldName = getWriterFieldName(elem);
            var fieldType = ParameterizedTypeName.get(JsonTypes.jsonWriter, subtypeTypeName(elem));
            var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(fieldType, fieldName);
            constructor.addStatement("this.$L = $L", fieldName, fieldName);
            typeBuilder.addField(readerField.build());
        });
        typeBuilder.addMethod(constructor.build());
    }

    /**
     * The subtype as written in the generated writer: the writer field type, the instanceof pattern and the cast must all agree.
     */
    private static TypeName subtypeTypeName(Element elem) {
        return TypeName.get(elem.asType());
    }

    private String getWriterFieldName(Element elem) {
        return decapitalize(elem.getSimpleName().toString() + "Writer");
    }
}
